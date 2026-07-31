import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    static List<Message> messages = new ArrayList<>();
    static Map<String, Map<String, String>> dictionaryByConcept = new HashMap<>();
    static Map<String, Map<String, String>> reverseDictionary = new HashMap<>();
    static List<String> supportedLanguages = Arrays.asList("en", "es", "fr", "de", "it", "pt", "ja", "zh", "ru", "ko");

    public static void main(String[] args) throws IOException {
        loadTranslations();
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/send", Main::handleSend);
        server.createContext("/messages", Main::handleGet);
        server.createContext("/", Main::serveStatic);

        server.setExecutor(null);
        server.start();
        System.out.println("Server running on http://localhost:" + port);
    }

    static void handleSend(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseJson(body);

        Message m = new Message();
        m.sender = data.getOrDefault("sender", "Anon");
        m.text = data.getOrDefault("text", "");
        m.lang = data.getOrDefault("lang", "en");
        if (m.lang == null || m.lang.isBlank()) {
            m.lang = "en";
        }
        m.lang = m.lang.toLowerCase();
        if (!supportedLanguages.contains(m.lang)) {
            m.lang = "en";
        }
        m.timestamp = System.currentTimeMillis();
        messages.add(m);

        String response = "{\"status\":\"ok\"}";
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
        OutputStream os = ex.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    static void handleGet(HttpExchange ex) throws IOException {
        String targetLang = "en";
        String query = ex.getRequestURI().getQuery(); // e.g. lang=en
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && kv[0].equals("lang") && !kv[1].isBlank()) {
                    targetLang = kv[1];
                    break;
                }
            }
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            String displayText = m.text;

            if (!m.lang.equals(targetLang)) {
                try {
                    displayText = translate(m.text, m.lang, targetLang);
                } catch (Exception e) {
                    displayText = m.text + " (translation failed)";
                }
            }

            json.append("{")
                .append("\"sender\":\"").append(escape(m.sender)).append("\",")
                .append("\"text\":\"").append(escape(displayText)).append("\",")
                .append("\"lang\":\"").append(escape(m.lang)).append("\",")
                .append("\"timestamp\":").append(m.timestamp)
                .append("}");
            if (i < messages.size() - 1) json.append(",");
        }
        json.append("]");

        byte[] respBytes = json.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, respBytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(respBytes);
        os.close();
    }

    static String translate(String text, String source, String target) throws IOException {
        if (source.equals(target)) {
            return text;
        }
        if (!reverseDictionary.containsKey(source) || !supportedLanguages.contains(target)) {
            return text;
        }

        Map<String, String> sourceMap = reverseDictionary.get(source);
        StringBuilder translated = new StringBuilder();
        Pattern tokenPattern = Pattern.compile("[\\p{L}0-9]+|[^\\p{L}0-9]+");
        Matcher matcher = tokenPattern.matcher(text);

        while (matcher.find()) {
            String token = matcher.group();
            if (token.matches("[\\p{L}0-9]+")) {
                String lower = token.toLowerCase();
                String concept = sourceMap.get(lower);
                if (concept != null) {
                    Map<String, String> translations = dictionaryByConcept.get(concept);
                    if (translations != null && translations.containsKey(target)) {
                        translated.append(preserveCase(token, translations.get(target)));
                        continue;
                    }
                }
            }
            translated.append(token);
        }

        return translated.toString();
    }

    static void loadTranslations() throws IOException {
        File file = new File("translations.json");
        if (!file.exists()) {
            throw new FileNotFoundException("translations.json not found in project root");
        }
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Pattern entryPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher entryMatcher = entryPattern.matcher(text);

        while (entryMatcher.find()) {
            String concept = entryMatcher.group(1);
            String inner = entryMatcher.group(2);
            Map<String, String> translations = new HashMap<>();
            Pattern innerPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
            Matcher innerMatcher = innerPattern.matcher(inner);
            while (innerMatcher.find()) {
                translations.put(innerMatcher.group(1), innerMatcher.group(2));
            }
            dictionaryByConcept.put(concept, translations);
        }

        for (Map.Entry<String, Map<String, String>> conceptEntry : dictionaryByConcept.entrySet()) {
            for (Map.Entry<String, String> langEntry : conceptEntry.getValue().entrySet()) {
                String lang = langEntry.getKey();
                String value = langEntry.getValue();
                reverseDictionary.computeIfAbsent(lang, k -> new HashMap<>())
                    .put(value.toLowerCase(), conceptEntry.getKey());
            }
        }
    }

    static String preserveCase(String original, String translated) {
        if (original.isEmpty()) return translated;
        if (Character.isUpperCase(original.charAt(0))) {
            return translated.substring(0, 1).toUpperCase() + translated.substring(1);
        }
        return translated;
    }

    static String unescapeJson(String text) {
        return text.replace("\\\"", "\"")
                   .replace("\\n", "\n")
                   .replace("\\r", "\r")
                   .replace("\\t", "\t")
                   .replace("\\\\", "\\");
    }

    static void serveStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        File file = new File("public" + path);
        if (!file.exists()) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        byte[] fileBytes = Files.readAllBytes(file.toPath());
        String contentType = path.endsWith(".css") ? "text/css"
                            : path.endsWith(".js") ? "application/javascript"
                            : "text/html";
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(200, fileBytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(fileBytes);
        os.close();
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // simple JSON parser for flat string objects like {"sender":"Bob","text":"Hi"}
    static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            map.put(matcher.group(1), unescapeJson(matcher.group(2)));
        }
        return map;
    }

    static class Message {
        String sender, text, lang;
        long timestamp;
    }
}

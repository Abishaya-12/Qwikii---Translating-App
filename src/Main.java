import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    static List<Message> messages = new ArrayList<>();
    static Map<String, Map<String, String>> dictionaryByConcept = new HashMap<>();
    static Map<String, Map<String, String>> reverseDictionary = new HashMap<>();
    static HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
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
                    targetLang = kv[1].toLowerCase();
                    break;
                }
            }
        }
        if (!supportedLanguages.contains(targetLang)) {
            targetLang = "en";
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

        try {
            return translateOnline(text, source, target);
        } catch (Exception e) {
            return translateOffline(text, source, target);
        }
    }

    static String translateOffline(String text, String source, String target) {
        if (!reverseDictionary.containsKey(source) || !supportedLanguages.contains(target)) {
            return text;
        }

        Map<String, String> sourceMap = reverseDictionary.get(source);
        List<String> tokens = new ArrayList<>();
        List<Boolean> isWord = new ArrayList<>();
        Pattern tokenPattern = Pattern.compile("[\\p{L}0-9'’]+|[^\\p{L}0-9'’]+");
        Matcher matcher = tokenPattern.matcher(text);

        while (matcher.find()) {
            String token = matcher.group();
            tokens.add(token);
            isWord.add(token.matches("[\\p{L}0-9'’]+"));
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < tokens.size()) {
            if (!isWord.get(i)) {
                result.append(tokens.get(i));
                i++;
                continue;
            }

            String bestTranslation = null;
            int bestWords = 0;
            int remainingWords = countRemainingWords(tokens, isWord, i);
            for (int wordCount = Math.min(5, remainingWords); wordCount > 0; wordCount--) {
                String candidate = joinWords(tokens, isWord, i, wordCount).toLowerCase();
                String concept = sourceMap.get(candidate);
                if (concept != null) {
                    Map<String, String> translations = dictionaryByConcept.get(concept);
                    if (translations != null && translations.containsKey(target)) {
                        bestTranslation = translations.get(target);
                        bestWords = wordCount;
                        break;
                    }
                }
            }

            if (bestTranslation != null) {
                result.append(preserveCase(tokens.get(i), bestTranslation));
                int wordsToConsume = bestWords;
                int j = i;
                while (j < tokens.size() && wordsToConsume > 0) {
                    if (isWord.get(j)) {
                        wordsToConsume--;
                    }
                    j++;
                }
                while (j < tokens.size() && !isWord.get(j)) {
                    result.append(tokens.get(j));
                    j++;
                }
                i = j;
                continue;
            }

            String token = tokens.get(i);
            String concept = sourceMap.get(token.toLowerCase());
            if (concept != null) {
                Map<String, String> translations = dictionaryByConcept.get(concept);
                if (translations != null && translations.containsKey(target)) {
                    result.append(preserveCase(token, translations.get(target)));
                    i++;
                    continue;
                }
            }

            result.append(token);
            i++;
        }

        return result.toString();
    }

    static String translateOnline(String text, String source, String target) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = String.format("https://translate.googleapis.com/translate_a/single?client=gtx&sl=%s&tl=%s&dt=t&q=%s", source, target, encoded);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .header("User-Agent", "Mozilla/5.0")
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Translation service returned " + response.statusCode());
        }

        Pattern p = Pattern.compile("\\[\\[\\[\"((?:\\\\.|[^\\\\\"])*)\"");
        Matcher matcher = p.matcher(response.body());
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        throw new IOException("Unexpected translation response");
    }

    static int countRemainingWords(List<String> tokens, List<Boolean> isWord, int start) {
        int count = 0;
        for (int i = start; i < tokens.size(); i++) {
            if (isWord.get(i)) {
                count++;
            }
        }
        return count;
    }

    static String joinWords(List<String> tokens, List<Boolean> isWord, int start, int wordCount) {
        StringBuilder builder = new StringBuilder();
        int currentWords = 0;
        for (int i = start; i < tokens.size() && currentWords < wordCount; i++) {
            if (!isWord.get(i)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(tokens.get(i));
            currentWords++;
        }
        return builder.toString();
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
        Pattern pattern = Pattern.compile("\"(sender|text|lang)\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*?)\"");
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

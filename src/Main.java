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

    public static void main(String[] args) throws IOException {
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
        m.sender = data.get("sender");
        m.text = data.get("text");
        m.lang = data.get("lang");
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

    static String translate(String text, String source, String target) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String reqBody = "q=" + URLEncoder.encode(text, "UTF-8")
                + "&source=" + URLEncoder.encode(source, "UTF-8")
                + "&target=" + URLEncoder.encode(target, "UTF-8")
                + "&format=text";

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://libretranslate.com/translate"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(reqBody))
            .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        String responseBody = res.body();

        Pattern pattern = Pattern.compile("\"translatedText\"\\s*:\\s*\"(.*?)\"");
        Matcher matcher = pattern.matcher(responseBody);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        throw new IOException("Unexpected translation response: " + responseBody);
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

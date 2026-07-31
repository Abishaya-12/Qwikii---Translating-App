import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.io.*;
import java.util.*;
import java.nio.file.*;

public class main {
    static List<Message> messages = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/send", exchange -> handleSend(exchange));
        server.createContext("/messages", exchange -> handleGet(exchange));
        server.createContext("/", exchange -> serveStatic(exchange)); // serves public/ folder

        server.setExecutor(null);
        server.start();
        System.out.println("Server running on http://localhost:8080");
    }

    static void handleSend(HttpExchange ex) throws IOException { /* ... */ }
    static void handleGet(HttpExchange ex) throws IOException { /* ... */ }
    static void serveStatic(HttpExchange ex) throws IOException { /* ... */ }

    static class Message {
        String sender, text, lang;
        long timestamp;
    }
}

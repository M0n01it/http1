package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private final int port;
    private final List<String> validPaths;
    private final ExecutorService threadPool;
    private volatile boolean isRunning = true; // Флаг состояния сервера
    private ServerSocket serverSocket;

    public Server(int port, List<String> validPaths) {
        this.port = port;
        this.validPaths = validPaths;
        this.threadPool = Executors.newFixedThreadPool(64);
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            logger.info("Сервер запущен на порту " + port);

            while (isRunning) { // Используем флаг состояния для контроля цикла
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (isRunning) { // Логируем ошибку, только если сервер все еще работает
                        logger.log(Level.SEVERE, "Ошибка при подключении клиента", e);
                    } else {
                        logger.info("Сервер закрывает соединение.");
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Не удалось запустить сервер", e);
        } finally {
            stop(); // Гарантируем, что ресурсы будут освобождены
            logger.info("Сервер остановлен");
        }
    }

    // Метод для остановки сервера
    public void stop() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Ошибка при закрытии серверного сокета", e);
            }
        }
        threadPool.shutdown();
    }

    private void handleClient(Socket clientSocket) {
        try (clientSocket;
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(clientSocket.getOutputStream())) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] tokens = requestLine.split(" ");
            if (tokens.length < 2) {
                sendResponse(out, 400, "Bad Request");
                return;
            }

            String method = tokens[0];
            String path = tokens[1];

            if (!validPaths.contains(path)) {
                sendResponse(out, 404, "Not Found");
                return;
            }

            if (method.equals("GET")) {
                Path filePath = Path.of(".", path);
                if (Files.exists(filePath)) {
                    byte[] content = Files.readAllBytes(filePath);
                    sendResponse(out, 200, "OK", content);
                } else {
                    sendResponse(out, 404, "Not Found");
                }
            } else {
                sendResponse(out, 405, "Method Not Allowed");
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Ошибка обработки клиента", e);
        }
    }

    private void sendResponse(BufferedOutputStream out, int statusCode, String statusText) throws IOException {
        sendResponse(out, statusCode, statusText, new byte[0]);
    }

    private void sendResponse(BufferedOutputStream out, int statusCode, String statusText, byte[] content) throws IOException {
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Length: " + content.length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
        out.write(content);
        out.flush();
    }
}
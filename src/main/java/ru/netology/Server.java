package ru.netology;

import org.apache.http.NameValuePair;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private final int port;
    private final List<String> validPaths;
    private final ExecutorService threadPool;
    private volatile boolean isRunning = true; // Флаг состояния сервера
    private ServerSocket serverSocket;

    public Server(int port, List<String> validPaths) {
        this.port = port;
        // Извлекаем только пути без Query для проверки
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
            String fullPath = tokens[1];

            // Создаем объект Request для обработки пути и параметров
            Request request = new Request(method, fullPath);
            logger.info("Получен запрос: " + request);

            // Используем метод getMethod() вместо локальной переменной
            String requestMethod = request.getMethod();

            if (!validPaths.contains(request.getPath())) {
                sendResponse(out, 404, "Not Found");
                return;
            }

            if (requestMethod.equals("GET")) {
                // Используем getQueryParam для получения параметра "last"
                Optional<String> lastParam = request.getQueryParam("last");
                lastParam.ifPresent(value -> logger.info("Параметр last = " + value));

                // Логируем все параметры запроса
                List<NameValuePair> queryParams = request.getQueryParams();
                if (!queryParams.isEmpty()) {
                    String params = queryParams.stream()
                            .map(param -> param.getName() + "=" + param.getValue())
                            .collect(Collectors.joining(", "));
                    logger.info("Все параметры запроса: " + params);
                }

                // Здесь можно добавить логику обработки параметра
                if (request.getPath().equals("/messages")) {
                    handleMessagesRequest(out, lastParam);
                    return;
                }

                Path filePath = Path.of(".", request.getPath());
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

    /**
     * Обрабатывает запросы к /messages с опциональным параметром last
     *
     * @param out        поток вывода для отправки ответа
     * @param lastParam  опциональное значение параметра last
     * @throws IOException если произошла ошибка ввода/вывода
     */
    private void handleMessagesRequest(BufferedOutputStream out, Optional<String> lastParam) throws IOException {
        List<String> allMessages = loadMessages();

        List<String> messagesToSend;
        if (lastParam.isPresent()) {
            try {
                int last = Integer.parseInt(lastParam.get());
                if (last < 0) {
                    sendResponse(out, 400, "Bad Request");
                    return;
                }
                int fromIndex = Math.max(allMessages.size() - last, 0);
                messagesToSend = allMessages.subList(fromIndex, allMessages.size());
            } catch (NumberFormatException e) {
                sendResponse(out, 400, "Bad Request");
                return;
            }
        } else {
            messagesToSend = allMessages;
        }

        String responseBody = String.join("\n", messagesToSend);
        byte[] content = responseBody.getBytes();

        sendResponse(out, 200, "OK", content);
    }

    /**
     * Загружает все сообщения из файла messages.txt
     *
     * @return список сообщений
     */
    private List<String> loadMessages() {
        Path messagesPath = Path.of("messages.txt");
        if (Files.exists(messagesPath)) {
            try {
                return Files.readAllLines(messagesPath);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Не удалось прочитать файл сообщений", e);
            }
        }
        return List.of();
    }

    private void sendResponse(BufferedOutputStream out, int statusCode, String statusText) throws IOException {
        sendResponse(out, statusCode, statusText, new byte[0]);
    }

    private void sendResponse(BufferedOutputStream out, int statusCode, String statusText, byte[] content) throws IOException {
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Length: " + content.length + "\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
        out.write(content);
        out.flush();
    }
}
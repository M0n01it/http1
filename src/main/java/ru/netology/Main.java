package ru.netology;

import java.util.List;

public class Main {
  public static void main(String[] args) throws InterruptedException {
    List<String> validPaths = List.of("/index.html", "/about.html", "/contact.html");
    Server server = new Server(8080, validPaths);
    new Thread(server::start).start();

    // Остановка сервера через 60 секунд
    Thread.sleep(60000);
    server.stop();
  }
}
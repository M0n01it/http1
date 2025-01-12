package ru.netology;

import java.util.List;
import java.util.Objects;

public class Main {
  public Main() {
  }

  public static void main(String[] args) throws InterruptedException {
    List<String> validPaths = List.of("/index.html", "/about.html", "/contact.html", "/messages");
    Server server = new Server(8080, validPaths);
    Objects.requireNonNull(server);
    (new Thread(server::start)).start();
    Thread.sleep(60000L);
    server.stop();
  }
}
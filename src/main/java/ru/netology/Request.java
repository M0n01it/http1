package ru.netology;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Request {
    private final String method;
    private final String path;
    private final List<NameValuePair> queryParams;

    public Request(String method, String fullPath) {
        this.method = method;
        String[] parts = fullPath.split("\\?", 2);
        this.path = parts[0];
        if (parts.length > 1) {
            this.queryParams = URLEncodedUtils.parse(parts[1], StandardCharsets.UTF_8);
        } else {
            this.queryParams = List.of();
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    /**
     * Возвращает значение параметра по его имени.
     * @param name имя параметра
     * @return значение параметра или пустой Optional, если параметр отсутствует
     */
    public Optional<String> getQueryParam(String name) {
        return queryParams.stream()
                .filter(param -> param.getName().equals(name))
                .map(NameValuePair::getValue)
                .findFirst();
    }

    /**
     * Возвращает список всех параметров запроса.
     * @return список NameValuePair
     */
    public List<NameValuePair> getQueryParams() {
        return queryParams;
    }

    @Override
    public String toString() {
        return "Request{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", queryParams=" + queryParams.stream()
                .map(param -> param.getName() + "=" + param.getValue())
                .collect(Collectors.joining(", ")) +
                '}';
    }
}
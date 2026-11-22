package com.catedra.turnos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "catalog")
public class CatalogoProperties {

    private String baseUrl = "http://localhost:8081";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 3000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs > 0 ? connectTimeoutMs : 2000;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : 3000;
    }

    public String getEffectiveBaseUrl() {
        return StringUtils.hasText(baseUrl) ? baseUrl.replaceAll("/+$", "") : "http://localhost:8081";
    }
}

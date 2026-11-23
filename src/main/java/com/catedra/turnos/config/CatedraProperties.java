package com.catedra.turnos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "catedra")
public class CatedraProperties {

    private String apiBaseUrl;
    private String jwtToken;
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 3000;

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public void setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = Math.max(1, connectTimeoutMs);
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = Math.max(1, readTimeoutMs);
    }

    public String getEffectiveBaseUrl() {
        return StringUtils.hasText(apiBaseUrl) ? apiBaseUrl.replaceAll("/+$", "") : "";
    }
}

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

    private String groupId;
    private String kafkaBootstrapServers;
    private String kafkaConsumerGroupId;
    private String kafkaAppointmentActionsTopic;
    private String kafkaAppointmentPhoneTopic;
    private String kafkaDltTopic;
    private String kafkaSecurityProtocol = "PLAINTEXT";
    private String kafkaSaslMechanism;
    private String kafkaSaslJaasConfig;
    private String kafkaSslTruststoreLocation;
    private String kafkaSslTruststorePassword;
    private String kafkaSslTruststoreType = "JKS";
    private String kafkaSslKeystoreLocation;
    private String kafkaSslKeystorePassword;
    private String kafkaSslKeystoreType = "JKS";
    private String kafkaSslKeyPassword;
    private String kafkaSslEndpointIdentificationAlgorithm;
    private boolean kafkaEnabled = true;
    private long kafkaRetryInitialMs = 1000;
    private long kafkaMaxRetries = 3;

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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public void setKafkaBootstrapServers(String kafkaBootstrapServers) {
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    public String getKafkaConsumerGroupId() {
        return kafkaConsumerGroupId;
    }

    public void setKafkaConsumerGroupId(String kafkaConsumerGroupId) {
        this.kafkaConsumerGroupId = kafkaConsumerGroupId;
    }

    public String getKafkaAppointmentActionsTopic() {
        return kafkaAppointmentActionsTopic;
    }

    public void setKafkaAppointmentActionsTopic(String kafkaAppointmentActionsTopic) {
        this.kafkaAppointmentActionsTopic = kafkaAppointmentActionsTopic;
    }

    public String getKafkaAppointmentPhoneTopic() {
        return kafkaAppointmentPhoneTopic;
    }

    public void setKafkaAppointmentPhoneTopic(String kafkaAppointmentPhoneTopic) {
        this.kafkaAppointmentPhoneTopic = kafkaAppointmentPhoneTopic;
    }

    public String getKafkaDltTopic() {
        return kafkaDltTopic;
    }

    public void setKafkaDltTopic(String kafkaDltTopic) {
        this.kafkaDltTopic = kafkaDltTopic;
    }

    public String getKafkaSecurityProtocol() {
        return StringUtils.hasText(kafkaSecurityProtocol) ? kafkaSecurityProtocol.toUpperCase() : "PLAINTEXT";
    }

    public void setKafkaSecurityProtocol(String kafkaSecurityProtocol) {
        this.kafkaSecurityProtocol = kafkaSecurityProtocol;
    }

    public String getKafkaSaslMechanism() {
        return kafkaSaslMechanism;
    }

    public void setKafkaSaslMechanism(String kafkaSaslMechanism) {
        this.kafkaSaslMechanism = kafkaSaslMechanism;
    }

    public String getKafkaSaslJaasConfig() {
        return kafkaSaslJaasConfig;
    }

    public void setKafkaSaslJaasConfig(String kafkaSaslJaasConfig) {
        this.kafkaSaslJaasConfig = kafkaSaslJaasConfig;
    }

    public String getKafkaSslTruststoreLocation() {
        return kafkaSslTruststoreLocation;
    }

    public void setKafkaSslTruststoreLocation(String kafkaSslTruststoreLocation) {
        this.kafkaSslTruststoreLocation = kafkaSslTruststoreLocation;
    }

    public String getKafkaSslTruststorePassword() {
        return kafkaSslTruststorePassword;
    }

    public void setKafkaSslTruststorePassword(String kafkaSslTruststorePassword) {
        this.kafkaSslTruststorePassword = kafkaSslTruststorePassword;
    }

    public String getKafkaSslTruststoreType() {
        return kafkaSslTruststoreType;
    }

    public void setKafkaSslTruststoreType(String kafkaSslTruststoreType) {
        this.kafkaSslTruststoreType = kafkaSslTruststoreType;
    }

    public String getKafkaSslKeystoreLocation() {
        return kafkaSslKeystoreLocation;
    }

    public void setKafkaSslKeystoreLocation(String kafkaSslKeystoreLocation) {
        this.kafkaSslKeystoreLocation = kafkaSslKeystoreLocation;
    }

    public String getKafkaSslKeystorePassword() {
        return kafkaSslKeystorePassword;
    }

    public void setKafkaSslKeystorePassword(String kafkaSslKeystorePassword) {
        this.kafkaSslKeystorePassword = kafkaSslKeystorePassword;
    }

    public String getKafkaSslKeystoreType() {
        return kafkaSslKeystoreType;
    }

    public void setKafkaSslKeystoreType(String kafkaSslKeystoreType) {
        this.kafkaSslKeystoreType = kafkaSslKeystoreType;
    }

    public String getKafkaSslKeyPassword() {
        return kafkaSslKeyPassword;
    }

    public void setKafkaSslKeyPassword(String kafkaSslKeyPassword) {
        this.kafkaSslKeyPassword = kafkaSslKeyPassword;
    }

    public String getKafkaSslEndpointIdentificationAlgorithm() {
        return kafkaSslEndpointIdentificationAlgorithm;
    }

    public void setKafkaSslEndpointIdentificationAlgorithm(String kafkaSslEndpointIdentificationAlgorithm) {
        this.kafkaSslEndpointIdentificationAlgorithm = kafkaSslEndpointIdentificationAlgorithm;
    }

    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }

    public void setKafkaEnabled(boolean kafkaEnabled) {
        this.kafkaEnabled = kafkaEnabled;
    }

    public long getKafkaRetryInitialMs() {
        return kafkaRetryInitialMs;
    }

    public void setKafkaRetryInitialMs(long kafkaRetryInitialMs) {
        this.kafkaRetryInitialMs = Math.max(1, kafkaRetryInitialMs);
    }

    public long getKafkaMaxRetries() {
        return kafkaMaxRetries;
    }

    public void setKafkaMaxRetries(long kafkaMaxRetries) {
        this.kafkaMaxRetries = Math.max(1, kafkaMaxRetries);
    }

    public String getEffectiveBaseUrl() {
        return StringUtils.hasText(apiBaseUrl) ? apiBaseUrl.replaceAll("/+$", "") : "";
    }

    public String getEffectiveGroupId() {
        return StringUtils.hasText(groupId) ? groupId : null;
    }

    public String getEffectiveConsumerGroupId() {
        if (StringUtils.hasText(kafkaConsumerGroupId)) {
            return kafkaConsumerGroupId;
        }
        String effectiveGroupId = getEffectiveGroupId();
        return effectiveGroupId == null ? null : "alumnos-" + effectiveGroupId;
    }

    public String getEffectiveActionsTopic() {
        if (StringUtils.hasText(kafkaAppointmentActionsTopic)) {
            return kafkaAppointmentActionsTopic;
        }
        String effectiveGroupId = getEffectiveGroupId();
        return effectiveGroupId == null ? null : "alumnos.turnos.acciones." + effectiveGroupId;
    }

    public String getEffectivePhoneTopic() {
        if (StringUtils.hasText(kafkaAppointmentPhoneTopic)) {
            return kafkaAppointmentPhoneTopic;
        }
        String effectiveGroupId = getEffectiveGroupId();
        return effectiveGroupId == null ? null : "catedra.turnos.telefono." + effectiveGroupId;
    }

    public String getEffectiveDltTopic() {
        if (StringUtils.hasText(kafkaDltTopic)) {
            return kafkaDltTopic;
        }
        String actionsTopic = getEffectiveActionsTopic();
        return actionsTopic == null ? null : actionsTopic + ".dlt";
    }
}

package com.catedra.turnos.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CatedraIntegrationEnvironmentPostProcessorTest {

    private final CatedraIntegrationEnvironmentPostProcessor processor =
            new CatedraIntegrationEnvironmentPostProcessor();

    @Test
    void loadsJwtAndIntegrationBeforeBinding() throws Exception {
        AtomicReference<String> loginBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/authenticate", exchange -> {
            loginBody.set(readBody(exchange));
            respond(exchange, 200, "{\"id_token\":\"jwt-tecnico\"}");
        });
        server.createContext("/api/student/integration", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {
                      "groupId": "proyecto-prueba",
                      "kafkaBootstrapServers": "kafka.test:9092",
                      "kafkaConsumerGroupId": "alumnos-proyecto-prueba",
                      "kafkaAppointmentActionsTopic": "alumnos.turnos.acciones.proyecto-prueba",
                      "kafkaAppointmentPhoneTopic": "catedra.turnos.telefono.proyecto-prueba",
                      "provisioningStatus": "PROVISIONED"
                    }
                    """);
        });
        server.start();

        try {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("CATEDRA_API_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/")
                    .withProperty("CATEDRA_LOGIN_USERNAME", "grupo.tecnico")
                    .withProperty("CATEDRA_LOGIN_PASSWORD", "secreto");

            processor.postProcessEnvironment(environment, null);

            assertThat(environment.getProperty("catedra.jwt-token")).isEqualTo("jwt-tecnico");
            assertThat(environment.getProperty("catedra.group-id")).isEqualTo("proyecto-prueba");
            assertThat(environment.getProperty("catedra.kafka-bootstrap-servers")).isEqualTo("kafka.test:9092");
            assertThat(environment.getProperty("catedra.kafka-consumer-group-id")).isEqualTo("alumnos-proyecto-prueba");
            assertThat(environment.getProperty("catedra.kafka-appointment-actions-topic"))
                    .isEqualTo("alumnos.turnos.acciones.proyecto-prueba");
            assertThat(environment.getProperty("catedra.kafka-appointment-phone-topic"))
                    .isEqualTo("catedra.turnos.telefono.proyecto-prueba");
            assertThat(authorization.get()).isEqualTo("Bearer jwt-tecnico");

            JsonNode request = new ObjectMapper().readTree(loginBody.get());
            assertThat(request.get("username").asText()).isEqualTo("grupo.tecnico");
            assertThat(request.get("password").asText()).isEqualTo("secreto");
            assertThat(request.get("rememberMe").asBoolean()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void skipsWhenCredentialsAreMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CATEDRA_API_URL", "http://catedra.test");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("catedra.jwt-token")).isNull();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

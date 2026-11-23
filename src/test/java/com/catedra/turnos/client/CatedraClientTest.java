package com.catedra.turnos.client;

import com.catedra.turnos.config.CatedraProperties;
import com.catedra.turnos.dto.catedra.OccupancyResponseDTO;
import com.catedra.turnos.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatedraClientTest {

    private CatedraProperties properties;
    private MockRestServiceServer server;
    private CatedraClient client;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        properties = new CatedraProperties();
        properties.setApiBaseUrl("http://catedra.test/");
        properties.setJwtToken("technical-token");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CatedraClient(builder.build(), properties, objectMapper);
    }

    @Test
    void getsOccupanciesWithTechnicalToken() {
        LocalDate from = LocalDate.of(2026, 11, 23);
        LocalDate to = LocalDate.of(2026, 11, 30);

        server.expect(requestTo("http://catedra.test/api/appointment-occupancies"
                        + "?professionalId=15&from=2026-11-23&to=2026-11-30"))
                .andExpect(header("Authorization", "Bearer technical-token"))
                .andRespond(withSuccess("""
                        {
                          "professionalId": 15,
                          "from": "2026-11-23",
                          "to": "2026-11-30",
                          "occupancies": [
                            {
                              "date": "2026-11-23",
                              "startTime": "09:00",
                              "status": "CONFIRMED",
                              "expiresAt": null
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        OccupancyResponseDTO response = client.getOccupancies(15L, from, to);

        assertThat(response.professionalId()).isEqualTo(15L);
        assertThat(response.occupancies()).hasSize(1);
        assertThat(response.occupancies().get(0).date()).isEqualTo(from);
        assertThat(response.occupancies().get(0).startTime()).isEqualTo(java.time.LocalTime.of(9, 0));
        assertThat(response.occupancies().get(0).status()).isEqualTo("CONFIRMED");
        server.verify();
    }

    @Test
    void mapsFunctionalErrorCodeFromProblemDetail() {
        LocalDate from = LocalDate.of(2026, 11, 23);
        LocalDate to = LocalDate.of(2026, 11, 22);

        server.expect(requestTo("http://catedra.test/api/appointment-occupancies"
                        + "?professionalId=15&from=2026-11-23&to=2026-11-22"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type":"about:blank","title":"Bad Request","status":400,
                                 "detail":"rango inválido","code":"INVALID_DATE_RANGE"}
                                """));

        assertThatThrownBy(() -> client.getOccupancies(15L, from, to))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_DATE_RANGE");
                });
        server.verify();
    }

    @Test
    void failsWhenTechnicalTokenIsMissing() {
        properties.setJwtToken("");

        assertThatThrownBy(() -> client.getOccupancies(
                15L,
                LocalDate.of(2026, 11, 23),
                LocalDate.of(2026, 11, 30)
        ))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo("INTEGRATION_NOT_CONFIGURED");
                });
        server.verify();
    }
}

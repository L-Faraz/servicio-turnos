package com.catedra.turnos.client;

import com.catedra.turnos.config.CatedraProperties;
import com.catedra.turnos.dto.catedra.ConfirmHoldRequestDTO;
import com.catedra.turnos.dto.catedra.HoldRequestDTO;
import com.catedra.turnos.dto.catedra.HoldResponseDTO;
import com.catedra.turnos.dto.catedra.OccupancyResponseDTO;
import com.catedra.turnos.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
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

    @Test
    void createsHoldWithTechnicalToken() {
        server.expect(requestTo("http://catedra.test/api/appointment-holds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer technical-token"))
                .andExpect(content().json("""
                        {
                          "professionalId": 15,
                          "date": "2026-11-23",
                          "startTime": "09:00:00",
                          "externalPatientId": "juan.perez"
                        }
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "holdId": "hold-1",
                                  "reservationProcessId": "process-1",
                                  "status": "HELD",
                                  "expiresAt": "2026-11-23T09:15:00Z"
                                }
                                """));

        HoldResponseDTO response = client.createHold(new HoldRequestDTO(
                15L,
                LocalDate.of(2026, 11, 23),
                java.time.LocalTime.of(9, 0),
                "juan.perez"
        ));

        assertThat(response.holdId()).isEqualTo("hold-1");
        assertThat(response.reservationProcessId()).isEqualTo("process-1");
        assertThat(response.status()).isEqualTo("HELD");
        server.verify();
    }

    @Test
    void confirmsHoldWithPatientData() {
        server.expect(requestTo("http://catedra.test/api/appointment-holds/hold-1/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer technical-token"))
                .andExpect(content().json("""
                        {
                          "externalPatientId": "juan.perez",
                          "patientFirstName": "Juan",
                          "patientLastName": "Perez"
                        }
                        """))
                .andRespond(withStatus(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "reservationProcessId": "process-1",
                                  "status": "WAITING_FOR_PHONE",
                                  "expiresAt": "2026-11-23T09:20:00Z"
                                }
                                """));

        var response = client.confirmHold("hold-1", new ConfirmHoldRequestDTO(
                "juan.perez", "Juan", "Perez"
        ));

        assertThat(response.status()).isEqualTo("WAITING_FOR_PHONE");
        server.verify();
    }

    @Test
    void findsAppointmentByProcessIdForReconciliation() {
        server.expect(requestTo("http://catedra.test/api/appointments"
                        + "?externalPatientId=juan.perez&page=0&size=100"))
                .andExpect(header("Authorization", "Bearer technical-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "reservationId": 8251,
                            "reservationProcessId": "process-2",
                            "externalPatientId": "otro.usuario",
                            "status": "CONFIRMED"
                          },
                          {
                            "reservationId": 8252,
                            "reservationProcessId": "process-1",
                            "externalPatientId": "juan.perez",
                            "status": "PHONE_PENDING"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var appointment = client.findAppointment("process-1", "juan.perez");

        assertThat(appointment).isPresent();
        assertThat(appointment.get().reservationId()).isEqualTo(8252L);
        assertThat(appointment.get().status()).isEqualTo("PHONE_PENDING");
        server.verify();
    }
}

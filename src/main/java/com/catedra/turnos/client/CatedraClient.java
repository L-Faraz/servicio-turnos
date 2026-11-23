package com.catedra.turnos.client;

import com.catedra.turnos.config.CatedraProperties;
import com.catedra.turnos.dto.CancelRequestDTO;
import com.catedra.turnos.dto.catedra.AppointmentPage;
import com.catedra.turnos.dto.catedra.CancelResponseDTO;
import com.catedra.turnos.dto.catedra.CatedraAppointmentDTO;
import com.catedra.turnos.dto.catedra.ConfirmHoldRequestDTO;
import com.catedra.turnos.dto.catedra.ConfirmHoldResponseDTO;
import com.catedra.turnos.dto.catedra.HoldRequestDTO;
import com.catedra.turnos.dto.catedra.HoldResponseDTO;
import com.catedra.turnos.dto.catedra.OccupancyResponseDTO;
import com.catedra.turnos.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.core.ParameterizedTypeReference;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class CatedraClient {

    private static final int APPOINTMENT_SEARCH_PAGE_SIZE = 100;
    private static final int MAX_APPOINTMENT_SEARCH_PAGES = 101;

    private final RestClient restClient;
    private final CatedraProperties properties;
    private final ObjectMapper objectMapper;

    public CatedraClient(
            @Qualifier("catedraRestClient") RestClient restClient,
            CatedraProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public OccupancyResponseDTO getOccupancies(Long professionalId, LocalDate from, LocalDate to) {
        String uri = UriComponentsBuilder.fromUriString(baseUrl())
                .path("/api/appointment-occupancies")
                .queryParam("professionalId", professionalId)
                .queryParam("from", from)
                .queryParam("to", to)
                .build()
                .toUriString();

        OccupancyResponseDTO response = execute(() -> restClient.get()
                .uri(uri)
                .headers(this::authorize)
                .retrieve()
                .body(OccupancyResponseDTO.class));

        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CATEDRA_ERROR", "Respuesta vacía de la catedra");
        }

        return new OccupancyResponseDTO(
                response.professionalId(),
                response.from(),
                response.to(),
                response.occupancies() == null ? List.of() : response.occupancies()
        );
    }

    public HoldResponseDTO createHold(HoldRequestDTO request) {
        return execute(() -> restClient.post()
                .uri(baseUrl() + "/api/appointment-holds")
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(HoldResponseDTO.class));
    }

    public ConfirmHoldResponseDTO confirmHold(String holdId, ConfirmHoldRequestDTO request) {
        return execute(() -> restClient.post()
                .uri(baseUrl() + "/api/appointment-holds/{holdId}/confirm", holdId)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ConfirmHoldResponseDTO.class));
    }

    public AppointmentPage listAppointments(
            String externalPatientId,
            String status,
            LocalDate from,
            LocalDate to,
            int page,
            int size,
            String sort
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl())
                .path("/api/appointments")
                .queryParam("externalPatientId", externalPatientId)
                .queryParam("page", page)
                .queryParam("size", size);

        if (StringUtils.hasText(status)) {
            builder.queryParam("status", status);
        }
        if (from != null) {
            builder.queryParam("from", from);
        }
        if (to != null) {
            builder.queryParam("to", to);
        }
        if (StringUtils.hasText(sort)) {
            builder.queryParam("sort", sort);
        }

        ResponseEntity<List<CatedraAppointmentDTO>> response = execute(() -> restClient.get()
                .uri(builder.build().toUriString())
                .headers(this::authorize)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                }));

        List<CatedraAppointmentDTO> content = response.getBody() == null ? List.of() : response.getBody();
        return new AppointmentPage(
                content,
                headerLong(response.getHeaders(), "X-Total-Count"),
                response.getHeaders().getFirst("Link")
        );
    }

    public Optional<CatedraAppointmentDTO> findAppointment(String processId, String externalPatientId) {
        Long total = null;
        int loaded = 0;
        for (int page = 0; page < MAX_APPOINTMENT_SEARCH_PAGES; page++) {
            AppointmentPage result = listAppointments(
                    externalPatientId,
                    null,
                    null,
                    null,
                    page,
                    APPOINTMENT_SEARCH_PAGE_SIZE,
                    null
            );
            List<CatedraAppointmentDTO> content = result.content() == null ? List.of() : result.content();
            if (page == 0) {
                total = result.totalCount();
            }

            Optional<CatedraAppointmentDTO> found = content.stream()
                    .filter(appointment -> appointment != null
                            && processId.equals(appointment.reservationProcessId()))
                    .findFirst();
            if (found.isPresent()) {
                return found;
            }

            loaded += content.size();
            boolean hasNext = hasNextLink(result.link());
            if (content.isEmpty()
                    || (total != null && loaded >= total && !hasNext)
                    || (total == null && !hasNext && content.size() < APPOINTMENT_SEARCH_PAGE_SIZE)) {
                return Optional.empty();
            }
        }
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "CATEDRA_PAGINATION_LIMIT",
                "No se pudo completar la búsqueda de reservas"
        );
    }

    private boolean hasNextLink(String link) {
        if (!StringUtils.hasText(link)) {
            return false;
        }
        return link.contains("rel=\"next\"") || link.contains("rel=next");
    }

    public CancelResponseDTO cancelAppointment(String processId, String reason) {
        RestClient.RequestBodySpec request = restClient.post()
                .uri(baseUrl() + "/api/appointments/{processId}/cancel", processId)
                .headers(this::authorize);
        RestClient.RequestBodySpec finalRequest = StringUtils.hasText(reason)
                ? request.contentType(MediaType.APPLICATION_JSON).body(new CancelRequestDTO(reason))
                : request;

        CancelResponseDTO response = execute(() -> finalRequest.retrieve().body(CancelResponseDTO.class));
        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "CATEDRA_ERROR", "Respuesta de cancelación vacía");
        }
        return response;
    }

    private Long headerLong(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void authorize(HttpHeaders headers) {
        if (!StringUtils.hasText(properties.getJwtToken())) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "INTEGRATION_NOT_CONFIGURED",
                    "CATEDRA_JWT_TOKEN no está configurado"
            );
        }
        headers.setBearerAuth(properties.getJwtToken());
    }

    private String baseUrl() {
        String baseUrl = properties.getEffectiveBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "INTEGRATION_NOT_CONFIGURED",
                    "CATEDRA_API_URL no está configurada"
            );
        }
        return baseUrl;
    }

    private <T> T execute(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
            String code = extractCode(exception.getResponseBodyAsString());
            throw new ApiException(
                    status != null ? status : HttpStatus.BAD_GATEWAY,
                    StringUtils.hasText(code) ? code : "CATEDRA_ERROR",
                    "La catedra rechazó la operación"
            );
        } catch (ResourceAccessException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CATEDRA_UNAVAILABLE",
                    "No se pudo contactar a la catedra"
            );
        } catch (RestClientException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CATEDRA_ERROR",
                    "Error inesperado al consultar la catedra"
            );
        }
    }

    private String extractCode(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode code = node.get("code");
            return code != null && code.isTextual() ? code.asText() : null;
        } catch (Exception exception) {
            return null;
        }
    }
}

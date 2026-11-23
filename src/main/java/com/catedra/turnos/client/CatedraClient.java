package com.catedra.turnos.client;

import com.catedra.turnos.config.CatedraProperties;
import com.catedra.turnos.dto.catedra.OccupancyResponseDTO;
import com.catedra.turnos.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

@Component
public class CatedraClient {

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

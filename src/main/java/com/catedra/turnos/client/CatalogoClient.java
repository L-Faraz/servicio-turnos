package com.catedra.turnos.client;

import com.catedra.turnos.config.CatalogoProperties;
import com.catedra.turnos.dto.catalogo.CatalogProfessionalDTO;
import com.catedra.turnos.dto.catalogo.CategoryDTO;
import com.catedra.turnos.dto.catalogo.ProfessionalPageDTO;
import com.catedra.turnos.dto.catalogo.WeeklyScheduleDTO;
import com.catedra.turnos.security.JwtService;
import com.catedra.turnos.web.ApiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Component
public class CatalogoClient {

    private final RestClient restClient;
    private final JwtService jwtService;
    private final CatalogoProperties properties;

    public CatalogoClient(@Qualifier("catalogoRestClient") RestClient catalogoRestClient, JwtService jwtService, CatalogoProperties properties) {
        this.restClient = catalogoRestClient;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    public List<CategoryDTO> getCategories() {
        return execute(() -> restClient.get()
                .uri("/api/categories?enabled=true")
                .headers(this::authorize)
                .retrieve()
                .body(new ParameterizedTypeReference<List<CategoryDTO>>() {
                }));
    }

    public ProfessionalPageDTO searchProfessionals(Long categoryId, String name, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/professionals")
                .queryParam("enabled", true)
                .queryParam("page", page)
                .queryParam("size", size);
        if (categoryId != null) {
            builder.queryParam("categoryId", categoryId);
        }
        if (name != null && !name.isBlank()) {
            builder.queryParam("name", name.trim());
        }

        return execute(() -> restClient.get()
                .uri(builder.build().toUri())
                .headers(this::authorize)
                .retrieve()
                .body(ProfessionalPageDTO.class));
    }

    public CatalogProfessionalDTO getProfessional(Long professionalId) {
        return execute(() -> restClient.get()
                .uri("/api/professionals/{id}", professionalId)
                .headers(this::authorize)
                .retrieve()
                .body(CatalogProfessionalDTO.class));
    }

    public List<WeeklyScheduleDTO> getSchedules(Long professionalId) {
        return execute(() -> restClient.get()
                .uri("/api/professionals/{id}/schedules", professionalId)
                .headers(this::authorize)
                .retrieve()
                .body(new ParameterizedTypeReference<List<WeeklyScheduleDTO>>() {
                }));
    }

    public boolean hasScheduleAt(Long professionalId, DayOfWeek dayOfWeek, LocalTime startTime) {
        return getSchedules(professionalId).stream()
                .anyMatch(schedule -> schedule.enabled()
                        && schedule.dayOfWeek() == dayOfWeek
                        && schedule.startTime() != null
                        && !schedule.startTime().isAfter(startTime));
    }

    private void authorize(org.springframework.http.HttpHeaders headers) {
        headers.setBearerAuth(jwtService.generateServiceToken());
    }

    private <T> T execute(java.util.function.Supplier<T> supplier) {
        try {
            T result = supplier.get();
            if (result == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "CATALOG_ERROR", "Respuesta vacía del catálogo");
            }
            return result;
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Recurso no encontrado en el catálogo");
            }
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CATALOG_UNAVAILABLE",
                    "El catálogo devolvió un error"
            );
        } catch (ResourceAccessException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CATALOG_UNAVAILABLE",
                    "No se pudo contactar al catálogo"
            );
        } catch (RestClientException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CATALOG_ERROR",
                    "Error inesperado al consultar el catálogo"
            );
        }
    }

    public String describeTarget() {
        return properties.getEffectiveBaseUrl();
    }
}

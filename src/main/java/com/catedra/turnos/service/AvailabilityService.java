package com.catedra.turnos.service;

import com.catedra.turnos.client.CatalogoClient;
import com.catedra.turnos.config.TurnosProperties;
import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.dto.AvailableSlotDTO;
import com.catedra.turnos.dto.catalogo.CatalogProfessionalDTO;
import com.catedra.turnos.dto.catalogo.WeeklyScheduleDTO;
import com.catedra.turnos.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AvailabilityService {

    private final CatalogoClient catalogoClient;
    private final TurnosProperties properties;

    public AvailabilityService(CatalogoClient catalogoClient, TurnosProperties properties) {
        this.catalogoClient = catalogoClient;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotDTO> getAvailability(AppUser user, Long professionalId, LocalDate from, LocalDate to) {
        validateRange(from, to);

        CatalogProfessionalDTO professional = catalogoClient.getProfessional(professionalId);
        if (!professional.enabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROFESSIONAL_DISABLED", "El profesional está deshabilitado");
        }

        List<WeeklyScheduleDTO> schedules = catalogoClient.getSchedules(professionalId).stream()
                .filter(WeeklyScheduleDTO::enabled)
                .toList();

        List<AvailableSlotDTO> slots = new ArrayList<>();
        LocalDate date = from;
        while (!date.isAfter(to)) {
            for (WeeklyScheduleDTO schedule : schedules) {
                if (schedule.dayOfWeek() != date.getDayOfWeek()
                        || schedule.startTime() == null
                        || schedule.endTime() == null
                        || !schedule.startTime().isBefore(schedule.endTime())) {
                    continue;
                }

                int duration = schedule.slotDurationMinutes() == null ? 30 : schedule.slotDurationMinutes();
                if (duration <= 0) {
                    continue;
                }

                LocalTime slotStart = schedule.startTime();
                while (slotStart.isBefore(schedule.endTime())) {
                    LocalTime slotEnd = slotStart.plusMinutes(duration);
                    if (slotEnd.isAfter(schedule.endTime())) {
                        slotEnd = schedule.endTime();
                    }

                    boolean past = date.isBefore(LocalDate.now())
                            || (date.isEqual(LocalDate.now()) && !slotStart.isAfter(LocalTime.now()));

                    slots.add(new AvailableSlotDTO(
                            professionalId,
                            date,
                            slotStart,
                            slotEnd,
                            !past,
                            past ? "PASADO" : null
                    ));

                    slotStart = slotStart.plusMinutes(duration);
                }
            }
            date = date.plusDays(1);
        }

        return slots;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "from y to son obligatorios");
        }
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "to no puede ser anterior a from");
        }
        if (from.isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "from no puede ser una fecha pasada");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        if (days > properties.getAvailability().getMaxDays()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "El rango no puede superar " + properties.getAvailability().getMaxDays() + " días"
            );
        }
    }
}

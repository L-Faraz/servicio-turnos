package com.catedra.turnos.service;

import com.catedra.turnos.client.CatalogoClient;
import com.catedra.turnos.client.CatedraClient;
import com.catedra.turnos.config.TurnosProperties;
import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.dto.AvailableSlotDTO;
import com.catedra.turnos.dto.catalogo.CatalogProfessionalDTO;
import com.catedra.turnos.dto.catalogo.WeeklyScheduleDTO;
import com.catedra.turnos.dto.catedra.OccupancyDTO;
import com.catedra.turnos.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AvailabilityService {

    private final CatalogoClient catalogoClient;
    private final CatedraClient catedraClient;
    private final TurnosProperties properties;

    public AvailabilityService(
            CatalogoClient catalogoClient,
            CatedraClient catedraClient,
            TurnosProperties properties
    ) {
        this.catalogoClient = catalogoClient;
        this.catedraClient = catedraClient;
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

        Map<SlotKey, String> occupiedSlots = loadOccupiedSlots(professionalId, from, to);
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
                    String occupancyStatus = occupiedSlots.get(new SlotKey(date, slotStart));

                    boolean available = !past && occupancyStatus == null;
                    String reason = past
                            ? "PASADO"
                            : occupancyStatus;

                    slots.add(new AvailableSlotDTO(
                            professionalId,
                            date,
                            slotStart,
                            slotEnd,
                            available,
                            reason
                    ));

                    slotStart = slotStart.plusMinutes(duration);
                }
            }
            date = date.plusDays(1);
        }

        return slots;
    }

    private Map<SlotKey, String> loadOccupiedSlots(Long professionalId, LocalDate from, LocalDate to) {
        List<OccupancyDTO> occupancies = catedraClient.getOccupancies(professionalId, from, to).occupancies();
        Map<SlotKey, String> result = new HashMap<>();
        Instant now = Instant.now();

        for (OccupancyDTO occupancy : occupancies) {
            if (occupancy == null || occupancy.date() == null || occupancy.startTime() == null) {
                continue;
            }

            String status = occupancy.status() == null ? "" : occupancy.status().trim().toUpperCase();
            if ("HELD".equals(status) && occupancy.expiresAt() != null && !occupancy.expiresAt().isAfter(now)) {
                continue;
            }
            if (!"CONFIRMED".equals(status) && !"HELD".equals(status)) {
                continue;
            }

            SlotKey key = new SlotKey(occupancy.date(), occupancy.startTime());
            if (!"CONFIRMED".equals(result.get(key))) {
                result.put(key, status);
            }
        }

        return result;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "from y to son obligatorios");
        }
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "to no puede ser anterior a from");
        }
        if (from.isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "from no puede ser una fecha pasada");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        if (days > properties.getAvailability().getMaxDays()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_RANGE",
                    "El rango no puede superar " + properties.getAvailability().getMaxDays() + " días"
            );
        }
    }

    private record SlotKey(LocalDate date, LocalTime startTime) {
    }
}

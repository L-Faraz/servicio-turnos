package com.catedra.turnos.service;

import com.catedra.turnos.client.CatalogoClient;
import com.catedra.turnos.client.CatedraClient;
import com.catedra.turnos.config.TurnosProperties;
import com.catedra.turnos.dto.AvailableSlotDTO;
import com.catedra.turnos.dto.catalogo.CatalogProfessionalDTO;
import com.catedra.turnos.dto.catalogo.WeeklyScheduleDTO;
import com.catedra.turnos.dto.catedra.OccupancyDTO;
import com.catedra.turnos.dto.catedra.OccupancyResponseDTO;
import com.catedra.turnos.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private CatalogoClient catalogoClient;

    @Mock
    private CatedraClient catedraClient;

    private final TurnosProperties properties = new TurnosProperties();

    private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        availabilityService = new AvailabilityService(catalogoClient, catedraClient, properties);
    }

    @Test
    void buildsSlotsWhenThereAreNoOccupancies() {
        LocalDate date = next(DayOfWeek.MONDAY);
        WeeklyScheduleDTO schedule = schedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        stubCatalog(date, schedule);
        stubOccupancies(date, List.of());

        List<AvailableSlotDTO> slots = availabilityService.getAvailability(null, 15L, date, date);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(0).available()).isTrue();
        assertThat(slots.get(1).startTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(slots.get(1).endTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void marksOccupiedSlotsAndIgnoresExpiredHolds() {
        LocalDate date = next(DayOfWeek.MONDAY);
        WeeklyScheduleDTO schedule = schedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0));
        stubCatalog(date, schedule);

        List<OccupancyDTO> occupancies = List.of(
                new OccupancyDTO(date, LocalTime.of(9, 0), "CONFIRMED", null),
                new OccupancyDTO(date, LocalTime.of(9, 30), "HELD", Instant.now().plusSeconds(600)),
                new OccupancyDTO(date, LocalTime.of(10, 0), "HELD", Instant.now().minusSeconds(60)),
                new OccupancyDTO(date, LocalTime.of(10, 30), "HELD", Instant.now().plusSeconds(600)),
                new OccupancyDTO(date, LocalTime.of(10, 30), "CONFIRMED", null)
        );
        stubOccupancies(date, occupancies);

        List<AvailableSlotDTO> slots = availabilityService.getAvailability(null, 15L, date, date);

        assertThat(slots).hasSize(4);
        assertThat(slots.get(0).available()).isFalse();
        assertThat(slots.get(0).reason()).isEqualTo("CONFIRMED");
        assertThat(slots.get(1).available()).isFalse();
        assertThat(slots.get(1).reason()).isEqualTo("HELD");
        assertThat(slots.get(2).available()).isTrue();
        assertThat(slots.get(2).reason()).isNull();
        assertThat(slots.get(3).available()).isFalse();
        assertThat(slots.get(3).reason()).isEqualTo("CONFIRMED");
    }

    @Test
    void rejectsInvalidDateRange() {
        LocalDate from = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> availabilityService.getAvailability(null, 15L, from, from))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("INVALID_DATE_RANGE");
    }

    private void stubCatalog(LocalDate date, WeeklyScheduleDTO schedule) {
        CatalogProfessionalDTO professional = new CatalogProfessionalDTO(
                15L, "Ana", "Gomez", 3L, "Cardiologia", true, List.of(schedule)
        );
        when(catalogoClient.getProfessional(15L)).thenReturn(professional);
        when(catalogoClient.getSchedules(15L)).thenReturn(List.of(schedule));
    }

    private void stubOccupancies(LocalDate date, List<OccupancyDTO> occupancies) {
        when(catedraClient.getOccupancies(15L, date, date)).thenReturn(
                new OccupancyResponseDTO(15L, date, date, occupancies)
        );
    }

    private WeeklyScheduleDTO schedule(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
        return new WeeklyScheduleDTO(1L, 15L, dayOfWeek, start, end, 30, true);
    }

    private LocalDate next(DayOfWeek dayOfWeek) {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return date;
    }
}

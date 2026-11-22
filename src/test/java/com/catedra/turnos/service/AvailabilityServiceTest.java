package com.catedra.turnos.service;

import com.catedra.turnos.client.CatalogoClient;
import com.catedra.turnos.config.TurnosProperties;
import com.catedra.turnos.dto.AvailableSlotDTO;
import com.catedra.turnos.dto.catalogo.CatalogProfessionalDTO;
import com.catedra.turnos.dto.catalogo.WeeklyScheduleDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private CatalogoClient catalogoClient;

    private final TurnosProperties properties = new TurnosProperties();

    private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        availabilityService = new AvailabilityService(catalogoClient, properties);
    }

    @Test
    void buildsSlotsFromCatalogSchedules() {
        LocalDate date = next(DayOfWeek.MONDAY);
        WeeklyScheduleDTO schedule = new WeeklyScheduleDTO(
                1L, 15L, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0), 30, true
        );
        CatalogProfessionalDTO professional = new CatalogProfessionalDTO(
                15L, "Ana", "Gomez", 3L, "Cardiologia", true, List.of(schedule)
        );

        when(catalogoClient.getProfessional(15L)).thenReturn(professional);
        when(catalogoClient.getSchedules(15L)).thenReturn(List.of(schedule));

        List<AvailableSlotDTO> slots = availabilityService.getAvailability(null, 15L, date, date);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(0).available()).isTrue();
        assertThat(slots.get(1).startTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(slots.get(1).endTime()).isEqualTo(LocalTime.of(10, 0));
    }

    private LocalDate next(DayOfWeek dayOfWeek) {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return date;
    }
}

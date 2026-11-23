package com.catedra.turnos.controller;

import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.dto.AppointmentPageDTO;
import com.catedra.turnos.dto.catedra.CancelResponseDTO;
import com.catedra.turnos.service.CurrentUserService;
import com.catedra.turnos.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppointmentControllerTest {

    @Mock
    private ReservationService reservationService;

    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;
    private AppUser user;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AppointmentController(reservationService, currentUserService)
        ).build();

        user = new AppUser();
        user.setId(1L);
        user.setLogin("juan.perez");
        authentication = mock(Authentication.class);
        when(currentUserService.get(authentication)).thenReturn(user);
    }

    @Test
    void listPropagatesPaginationHeaders() throws Exception {
        when(reservationService.listAppointments(
                eq(user),
                eq("CONFIRMED"),
                eq(LocalDate.of(2026, 11, 23)),
                eq(LocalDate.of(2026, 11, 30)),
                eq(0),
                eq(20),
                eq(null)
        )).thenReturn(new AppointmentPageDTO(
                List.of(),
                7L,
                "<http://catedra.test/api/appointments?page=1>; rel=\"next\""
        ));

        mockMvc.perform(get("/api/appointments")
                        .principal(authentication)
                        .param("status", "CONFIRMED")
                        .param("from", "2026-11-23")
                        .param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "7"))
                .andExpect(header().string("Link", "<http://catedra.test/api/appointments?page=1>; rel=\"next\""))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void cancelReturnsCancelledState() throws Exception {
        when(reservationService.cancelAppointment(user, "process-1", "motivo"))
                .thenReturn(new CancelResponseDTO(
                        8251L, "process-1", "CANCELLED", Instant.now(), "motivo"
                ));

        mockMvc.perform(post("/api/appointments/process-1/cancel")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"motivo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationProcessId").value("process-1"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(reservationService).cancelAppointment(user, "process-1", "motivo");
    }

    @Test
    void cancelAcceptsEmptyBody() throws Exception {
        when(reservationService.cancelAppointment(user, "process-1", null))
                .thenReturn(new CancelResponseDTO(
                        8251L, "process-1", "CANCELLED", Instant.now(), null
                ));

        mockMvc.perform(post("/api/appointments/process-1/cancel")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(reservationService).cancelAppointment(user, "process-1", null);
    }
}

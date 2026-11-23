package com.catedra.turnos.controller;

import com.catedra.turnos.dto.AppointmentPageDTO;
import com.catedra.turnos.dto.CancelRequestDTO;
import com.catedra.turnos.dto.catedra.CancelResponseDTO;
import com.catedra.turnos.service.CurrentUserService;
import com.catedra.turnos.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final ReservationService reservationService;
    private final CurrentUserService currentUserService;

    public AppointmentController(ReservationService reservationService, CurrentUserService currentUserService) {
        this.reservationService = reservationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<AppointmentPageDTO> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            Authentication authentication
    ) {
        AppointmentPageDTO result = reservationService.listAppointments(
                currentUserService.get(authentication),
                status,
                from,
                to,
                page,
                size,
                sort
        );

        return ResponseEntity.ok()
                .headers(headers -> {
                    if (result.totalCount() != null) {
                        headers.add("X-Total-Count", result.totalCount().toString());
                    }
                    if (result.link() != null) {
                        headers.add("Link", result.link());
                    }
                })
                .body(result);
    }

    @PostMapping("/{processId}/cancel")
    public ResponseEntity<CancelResponseDTO> cancel(
            @PathVariable String processId,
            @Valid @RequestBody(required = false) CancelRequestDTO request,
            Authentication authentication
    ) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(reservationService.cancelAppointment(
                currentUserService.get(authentication),
                processId,
                reason
        ));
    }
}

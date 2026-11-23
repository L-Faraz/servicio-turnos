package com.catedra.turnos.controller;

import com.catedra.turnos.dto.SubmitPhoneRequest;
import com.catedra.turnos.service.CurrentUserService;
import com.catedra.turnos.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationPhoneController {

    private final ReservationService reservationService;
    private final CurrentUserService currentUserService;

    public ReservationPhoneController(
            ReservationService reservationService,
            CurrentUserService currentUserService
    ) {
        this.reservationService = reservationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/{processId}/phone")
    public ResponseEntity<Void> submitPhone(
            @PathVariable String processId,
            @Valid @RequestBody SubmitPhoneRequest request,
            Authentication authentication
    ) {
        reservationService.submitPhone(currentUserService.get(authentication), processId, request.phoneNumber());
        return ResponseEntity.accepted().build();
    }
}

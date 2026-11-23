package com.catedra.turnos.controller;

import com.catedra.turnos.dto.ConfirmedHoldResponse;
import com.catedra.turnos.dto.CreateHoldRequest;
import com.catedra.turnos.dto.CreatedHoldResponse;
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

import java.net.URI;

@RestController
@RequestMapping("/api/appointment-holds")
public class AppointmentHoldController {

    private final ReservationService reservationService;
    private final CurrentUserService currentUserService;

    public AppointmentHoldController(ReservationService reservationService, CurrentUserService currentUserService) {
        this.reservationService = reservationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<CreatedHoldResponse> createHold(
            @Valid @RequestBody CreateHoldRequest request,
            Authentication authentication
    ) {
        CreatedHoldResponse response = reservationService.createHold(currentUserService.get(authentication), request);
        return ResponseEntity
                .created(URI.create("/api/appointment-holds/" + response.holdId()))
                .body(response);
    }

    @PostMapping("/{holdId}/confirm")
    public ResponseEntity<ConfirmedHoldResponse> confirmHold(
            @PathVariable String holdId,
            Authentication authentication
    ) {
        return ResponseEntity.accepted()
                .body(reservationService.confirmHold(currentUserService.get(authentication), holdId));
    }
}

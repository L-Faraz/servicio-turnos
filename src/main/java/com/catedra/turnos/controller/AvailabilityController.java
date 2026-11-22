package com.catedra.turnos.controller;

import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.dto.AvailableSlotDTO;
import com.catedra.turnos.service.AvailabilityService;
import com.catedra.turnos.service.CurrentUserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final CurrentUserService currentUserService;

    public AvailabilityController(AvailabilityService availabilityService, CurrentUserService currentUserService) {
        this.availabilityService = availabilityService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/availability")
    public List<AvailableSlotDTO> availability(
            Authentication authentication,
            @RequestParam Long professionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        AppUser user = currentUserService.get(authentication);
        return availabilityService.getAvailability(user, professionalId, from, to);
    }
}

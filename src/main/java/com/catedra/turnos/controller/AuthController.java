package com.catedra.turnos.controller;

import com.catedra.turnos.dto.auth.AuthResponse;
import com.catedra.turnos.dto.auth.LoginRequest;
import com.catedra.turnos.dto.auth.RegisterRequest;
import com.catedra.turnos.dto.auth.UserResponse;
import com.catedra.turnos.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.created(URI.create("/api/me")).body(response);
    }

    @PostMapping("/authenticate")
    public AuthResponse authenticate(@Valid @RequestBody LoginRequest request) {
        return authService.authenticate(request);
    }
}

package com.catedra.turnos.service;

import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.repository.AppUserRepository;
import com.catedra.turnos.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final AppUserRepository userRepository;

    public CurrentUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AppUser get(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Autenticación requerida");
        }

        return userRepository.findByLogin(authentication.getName())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "USER_NOT_FOUND",
                        "El usuario autenticado no existe"
                ));
    }
}

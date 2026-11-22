package com.catedra.turnos.service;

import com.catedra.turnos.config.SecurityProperties;
import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.domain.Authority;
import com.catedra.turnos.dto.auth.AuthResponse;
import com.catedra.turnos.dto.auth.LoginRequest;
import com.catedra.turnos.dto.auth.RegisterRequest;
import com.catedra.turnos.dto.auth.UserResponse;
import com.catedra.turnos.repository.AppUserRepository;
import com.catedra.turnos.repository.AuthorityRepository;
import com.catedra.turnos.security.JwtService;
import com.catedra.turnos.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class AuthService {

    private static final String ROLE_USER = "ROLE_USER";

    private final AppUserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;

    public AuthService(AppUserRepository userRepository,
                       AuthorityRepository authorityRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String login = request.login().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByLogin(login)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOGIN_ALREADY_USED", "El login ya está en uso");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_ALREADY_USED", "El email ya está en uso");
        }

        Authority userAuthority = authorityRepository.findByName(ROLE_USER)
                .orElseGet(() -> authorityRepository.save(new Authority(ROLE_USER)));

        Instant now = Instant.now();
        AppUser user = new AppUser();
        user.setLogin(login);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setImageUrl(request.imageUrl());
        user.setLangKey(request.langKey() == null || request.langKey().isBlank()
                ? "es"
                : request.langKey().trim().toLowerCase(Locale.ROOT));
        user.setActivated(true);
        user.setCreatedBy(login);
        user.setCreatedDate(now);
        user.setLastModifiedBy(login);
        user.setLastModifiedDate(now);
        user.setAuthorities(new HashSet<>(Set.of(userAuthority)));

        return toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse authenticate(LoginRequest request) {
        String login = request.login().trim().toLowerCase(Locale.ROOT);
        AppUser user = userRepository.findByLogin(login)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "BAD_CREDENTIALS",
                        "Login o contraseña inválidos"
                ));

        if (!user.isActivated() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "BAD_CREDENTIALS",
                    "Login o contraseña inválidos"
            );
        }

        String token = jwtService.generateUserToken(user.getLogin(), user.getRoles());
        return new AuthResponse(
                token,
                "Bearer",
                securityProperties.getJwt().getExpirationMs() / 1000
        );
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getLogin(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getImageUrl(),
                user.getLangKey(),
                user.getRoles()
        );
    }
}

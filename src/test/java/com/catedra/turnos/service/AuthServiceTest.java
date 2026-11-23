package com.catedra.turnos.service;

import com.catedra.turnos.config.SecurityProperties;
import com.catedra.turnos.domain.AppUser;
import com.catedra.turnos.domain.Authority;
import com.catedra.turnos.dto.auth.LoginRequest;
import com.catedra.turnos.dto.auth.RegisterRequest;
import com.catedra.turnos.repository.AppUserRepository;
import com.catedra.turnos.repository.AuthorityRepository;
import com.catedra.turnos.security.JwtService;
import com.catedra.turnos.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private AuthorityRepository authorityRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private SecurityProperties securityProperties;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerNormalizesIdentityAndAssignsUserRole() {
        when(userRepository.existsByLogin("juan.perez")).thenReturn(false);
        when(userRepository.existsByEmail("juan@example.com")).thenReturn(false);
        when(authorityRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());
        when(authorityRepository.save(any(Authority.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("clave-segura")).thenReturn("hash-bcrypt");
        when(userRepository.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(new RegisterRequest(
                "Juan.Perez",
                "Juan@Example.com",
                "clave-segura",
                "Juan",
                "Perez",
                null,
                "ES"
        ));

        assertThat(response.login()).isEqualTo("juan.perez");
        assertThat(response.email()).isEqualTo("juan@example.com");
        assertThat(response.langKey()).isEqualTo("es");
        assertThat(response.roles()).containsExactly("USER");
    }

    @Test
    void registerRejectsDuplicatedLogin() {
        when(userRepository.existsByLogin("juan.perez")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "juan.perez", "juan@example.com", "clave-segura", "Juan", "Perez", null, "es"
        )))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("LOGIN_ALREADY_USED");
    }

    @Test
    void authenticateIssuesTokenForValidCredentials() {
        AppUser user = user("juan.perez", "hash-bcrypt");
        when(userRepository.findByLogin("juan.perez")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("clave-segura", "hash-bcrypt")).thenReturn(true);
        when(jwtService.generateUserToken("juan.perez", Set.of("USER"))).thenReturn("jwt-token");
        when(securityProperties.getJwt()).thenReturn(new SecurityProperties.Jwt());

        var response = authService.authenticate(new LoginRequest("Juan.Perez", "clave-segura"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void authenticateRejectsWrongPassword() {
        when(userRepository.findByLogin("juan.perez")).thenReturn(Optional.of(user("juan.perez", "hash-bcrypt")));
        when(passwordEncoder.matches("mala", "hash-bcrypt")).thenReturn(false);

        assertThatThrownBy(() -> authService.authenticate(new LoginRequest("juan.perez", "mala")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private AppUser user(String login, String passwordHash) {
        AppUser user = new AppUser();
        user.setLogin(login);
        user.setPasswordHash(passwordHash);
        user.setActivated(true);
        user.setAuthorities(new java.util.HashSet<>(Set.of(new Authority("ROLE_USER"))));
        return user;
    }
}

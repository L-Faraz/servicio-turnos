package com.catedra.turnos.security;

import com.catedra.turnos.config.SecurityProperties;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void generatesSignedTokenWithIssuerRolesAndExpiration() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret("test-secret-with-at-least-32-bytes-for-hs256");
        properties.getJwt().setIssuer("servicio-turnos");
        properties.getJwt().setExpirationMs(60_000L);

        String serialized = new JwtService(properties).generateUserToken("juan.perez", List.of("USER"));

        SignedJWT jwt = SignedJWT.parse(serialized);
        assertThat(jwt.verify(new MACVerifier(
                "test-secret-with-at-least-32-bytes-for-hs256".getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("juan.perez");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("servicio-turnos");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("USER");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isAfter(new java.util.Date());
    }

    @Test
    void generatesServiceTokenWithServiceRole() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret("test-secret-with-at-least-32-bytes-for-hs256");
        properties.getJwt().setIssuer("servicio-turnos");

        SignedJWT jwt = SignedJWT.parse(new JwtService(properties).generateServiceToken());

        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("servicio-turnos");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("SERVICE");
    }
}

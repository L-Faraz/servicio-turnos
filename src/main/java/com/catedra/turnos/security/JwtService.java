package com.catedra.turnos.security;

import com.catedra.turnos.config.SecurityProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private static final int MIN_HS256_SECRET_BYTES = 32;

    private final SecurityProperties properties;
    private final byte[] secretBytes;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;

        String secret = properties.getJwt().getSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "app.security.jwt.secret es obligatorio. Configurar la variable APP_JWT_SECRET.");
        }

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_HS256_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret debe tener al menos " + MIN_HS256_SECRET_BYTES + " bytes para HS256.");
        }

        this.secretBytes = bytes;
    }

    public String generateUserToken(String subject, Collection<String> roles) {
        return generate(subject, roles);
    }

    public String generateServiceToken() {
        return generate("servicio-turnos", List.of("SERVICE"));
    }

    private String generate(String subject, Collection<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(properties.getJwt().getIssuer())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusMillis(properties.getJwt().getExpirationMs())))
                .jwtID(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new MACSigner(secretBytes));
        } catch (JOSEException exception) {
            throw new IllegalStateException("No se pudo firmar el JWT", exception);
        }

        return jwt.serialize();
    }
}

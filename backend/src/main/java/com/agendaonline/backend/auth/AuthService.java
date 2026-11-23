package com.agendaonline.backend.auth;

import com.agendaonline.backend.auth.AuthDtos.LoginResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confere e-mail e senha do administrador e emite um token JWT assinado. */
@Service
public class AuthService {

    public static final String ISSUER = "agenda-online";
    public static final String ROLES_CLAIM = "roles";

    private final AdminUserRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Duration tokenTtl;

    /** Hash de uma senha qualquer: usado quando o e-mail não existe, para a resposta levar o mesmo tempo. */
    private final String dummyHash;

    public AuthService(AdminUserRepository admins, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            @Value("${app.jwt.ttl}") Duration tokenTtl) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.tokenTtl = tokenTtl;
        this.dummyHash = passwordEncoder.encode("senha-inexistente");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String email, String password) {
        AdminUser admin = admins.findByEmailIgnoreCase(email.trim()).orElse(null);
        String hash = admin != null ? admin.getPasswordHash() : dummyHash;
        boolean ok = passwordEncoder.matches(password, hash);
        if (admin == null || !ok) {
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(tokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(admin.getEmail())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLES_CLAIM, List.of("ADMIN"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", expiresAt);
    }
}

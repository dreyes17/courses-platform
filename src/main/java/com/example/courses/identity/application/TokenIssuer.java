package com.example.courses.identity.application;

import com.example.courses.identity.domain.UserAccount;
import com.example.courses.shared.security.JwtConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class TokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final Duration ttl;

    public TokenIssuer(JwtEncoder jwtEncoder, @Value("${app.jwt.ttl:1h}") Duration ttl) {
        this.jwtEncoder = jwtEncoder;
        this.ttl = ttl;
    }

    public AccessToken issueFor(UserAccount user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("email", user.getEmail())
                .claim(JwtConfig.ROLES_CLAIM, List.of(user.getRole().name()));
        if (user.getStudentId() != null) {
            claims.claim(JwtConfig.STUDENT_ID_CLAIM, user.getStudentId().toString());
        }
        if (user.getInstructorId() != null) {
            claims.claim(JwtConfig.INSTRUCTOR_ID_CLAIM, user.getInstructorId().toString());
        }
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new AccessToken(token, "Bearer", ttl.toSeconds());
    }
}

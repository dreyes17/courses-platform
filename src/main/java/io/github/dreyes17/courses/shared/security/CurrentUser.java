package io.github.dreyes17.courses.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/** The authenticated caller, read from the validated JWT claims. */
public record CurrentUser(UUID userId, List<String> roles, UUID studentId, UUID instructorId) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsStringList(JwtConfig.ROLES_CLAIM),
                uuidClaim(jwt, JwtConfig.STUDENT_ID_CLAIM),
                uuidClaim(jwt, JwtConfig.INSTRUCTOR_ID_CLAIM));
    }

    public static CurrentUser from(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return from(jwt);
        }
        return null;
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    public boolean isStudent() {
        return roles.contains("STUDENT");
    }

    private static UUID uuidClaim(Jwt jwt, String claim) {
        String value = jwt.getClaimAsString(claim);
        return value == null ? null : UUID.fromString(value);
    }
}

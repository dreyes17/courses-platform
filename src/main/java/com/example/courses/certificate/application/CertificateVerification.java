package com.example.courses.certificate.application;

import java.time.Instant;

/**
 * What anyone holding a certificate code can confirm without an account: who earned it, for which course and
 * when. Deliberately no ids or email, since it is public.
 */
public record CertificateVerification(
        String code,
        String holderName,
        String courseTitle,
        Instant issuedAt
) {
}

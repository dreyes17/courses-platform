package com.example.courses.certificate.mcp;

import com.example.courses.certificate.application.CertificateService;
import com.example.courses.certificate.application.CertificateVerification;
import com.example.courses.certificate.application.CertificateView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

/** MCP adapter for certificates, like {@code certificate.web.CertificateController}. */
@Component
@Validated
class CertificateTools {

    private final CertificateService certificates;

    CertificateTools(CertificateService certificates) {
        this.certificates = certificates;
    }

    @McpTool(name = "get_enrollment_certificate", description = """
            Get the certificate of a COMPLETED enrollment, with its verifiable code. It is issued asynchronously \
            after the enrollment completes, so it may not exist for a moment right after reaching progress 100. \
            ADMIN, its student, or the course's instructor.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    @PreAuthorize("hasRole('ADMIN') or @access.canViewEnrollment(authentication, #enrollmentId)")
    public CertificateView getEnrollmentCertificate(
            @McpToolParam(description = "Enrollment id (UUID)") @NotNull UUID enrollmentId) {
        return certificates.getForEnrollment(enrollmentId);
    }

    @McpTool(name = "verify_certificate", description = """
            Check that a certificate code is genuine: returns the holder's name, the course and the issue date. \
            Any user.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    public CertificateVerification verifyCertificate(
            @McpToolParam(description = "Certificate code, e.g. CERT-1A2B3C4D5E6F7A8B") @NotBlank @Size(max = 50)
            String code) {
        return certificates.verify(code);
    }
}

package com.example.courses.certificate.web;

import com.example.courses.certificate.application.CertificateService;
import com.example.courses.certificate.application.CertificateVerification;
import com.example.courses.certificate.application.CertificateView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Certificates")
class CertificateController {

    private final CertificateService certificates;

    CertificateController(CertificateService certificates) {
        this.certificates = certificates;
    }

    @GetMapping("/api/enrollments/{id}/certificate")
    @PreAuthorize("hasRole('ADMIN') or @access.canViewEnrollment(authentication, #id)")
    @Operation(summary = "Get the certificate of a completed enrollment. ADMIN, its student, or the course's instructor",
            description = "The certificate is issued asynchronously once the enrollment reaches COMPLETED, so right "
                    + "after completing it this can briefly return 404. Its code can be verified by anyone with "
                    + "GET /api/certificates/{code}.")
    @ApiResponse(responseCode = "200", description = "The certificate and its verifiable code")
    @ApiResponse(responseCode = "404",
            description = "The enrollment doesn't exist, or its certificate hasn't been issued (yet)")
    CertificateView getForEnrollment(@PathVariable UUID id) {
        return certificates.getForEnrollment(id);
    }

    @GetMapping("/api/certificates/{code}")
    @SecurityRequirements
    @Operation(summary = "Verify a certificate by its code (public)",
            description = "Lets anyone shown a certificate, such as an employer, confirm it is genuine without an "
                    + "account: returns who earned it, for which course and when. Codes are random and "
                    + "unguessable.")
    @ApiResponse(responseCode = "200", description = "The certificate is genuine")
    @ApiResponse(responseCode = "404", description = "No certificate has this code")
    CertificateVerification verify(@PathVariable @Size(max = 50) String code) {
        return certificates.verify(code);
    }
}

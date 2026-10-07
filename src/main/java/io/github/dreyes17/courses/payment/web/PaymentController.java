package io.github.dreyes17.courses.payment.web;

import io.github.dreyes17.courses.payment.application.PaymentService;
import io.github.dreyes17.courses.payment.application.PaymentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Payments")
class PaymentController {

    private final PaymentService payments;

    PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/api/enrollments/{id}/payment")
    @PreAuthorize("hasRole('ADMIN') or @access.ownsEnrollment(authentication, #id)")
    @Operation(summary = "Get the payment of an enrollment. ADMIN or its student",
            description = "The payment is created PENDING with the enrollment and settled asynchronously: CONFIRMED "
                    + "activates the enrollment; FAILED cancels it, and failureReason says why.")
    @ApiResponse(responseCode = "200", description = "The payment, with failureReason when it FAILED")
    PaymentView get(@PathVariable UUID id) {
        return payments.getForEnrollment(id);
    }
}

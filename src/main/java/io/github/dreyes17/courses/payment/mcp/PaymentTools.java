package io.github.dreyes17.courses.payment.mcp;

import io.github.dreyes17.courses.payment.application.PaymentService;
import io.github.dreyes17.courses.payment.application.PaymentView;
import jakarta.validation.constraints.NotNull;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

/** MCP adapter for payments, like {@code payment.web.PaymentController}. */
@Component
@Validated
class PaymentTools {

    private final PaymentService payments;

    PaymentTools(PaymentService payments) {
        this.payments = payments;
    }

    @McpTool(name = "get_enrollment_payment", description = """
            Get the payment of an enrollment: amount, currency and status (PENDING, CONFIRMED or FAILED). A FAILED \
            payment cancels the enrollment, and failureReason says why. ADMIN or its student.""",
            annotations = @McpAnnotations(readOnlyHint = true))
    @PreAuthorize("hasRole('ADMIN') or @access.ownsEnrollment(authentication, #enrollmentId)")
    public PaymentView getEnrollmentPayment(
            @McpToolParam(description = "Enrollment id (UUID)") @NotNull UUID enrollmentId) {
        return payments.getForEnrollment(enrollmentId);
    }
}

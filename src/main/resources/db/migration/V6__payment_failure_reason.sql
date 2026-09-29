-- Why a payment failed (the gateway's decline reason, or why it was never charged), so the client that sees its
-- enrollment CANCELLED can find out why through GET /api/enrollments/{id}/payment. Set exactly when status = 'FAILED'.
ALTER TABLE payments ADD COLUMN failure_reason TEXT;
-- Payments that failed before this column existed only have their reason in the logs.
UPDATE payments SET failure_reason = 'Not recorded' WHERE status = 'FAILED';
ALTER TABLE payments ADD CONSTRAINT chk_payments_failure_reason
    CHECK ((status = 'FAILED') = (failure_reason IS NOT NULL));

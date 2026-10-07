# Concurrency and idempotency

- [Seat reservation](#seat-reservation)
- [Request idempotency (`Idempotency-Key`)](#request-idempotency-idempotency-key)

## Seat reservation

**Strategy: an atomic conditional update in SQL.**

```sql
UPDATE courses SET seats_taken = seats_taken + 1, version = version + 1
 WHERE id = ? AND status = 'PUBLISHED' AND seats_taken < capacity
```

If the statement updates 0 rows, there's no seat (or the course isn't published). The service then loads the
course and `Course.assertAcceptsEnrollment()` decides which domain exception applies: `CourseFullException` if
it's full, or `InvalidStateTransitionException` if it isn't `PUBLISHED`. Both return 409.

Why this one and not the two usual alternatives:

- **Versus optimistic locking (`@Version` + retry):** under high contention, such as a popular course launch,
  almost every transaction would fail and retry. The conditional statement never needs a retry: Postgres
  serialises writes on the row and each one evaluates the `WHERE` against the already-updated value.
- **Versus pessimistic locking (`SELECT ... FOR UPDATE`):** same guarantee, but with two round trips to the
  database and the lock held while the application decides. The conditional statement checks and writes in a
  single step.
- **The database is the last line of defence:** even if a bug skipped the statement,
  `CHECK (seats_taken <= capacity)` would reject the overflow.

The `version` column is kept and the statement increments it. The reason: an instructor editing a course uses
optimistic locking and writes every column, `seats_taken` included. Without that increment, a concurrent edit
could overwrite `seats_taken` with a value read before a reservation.

Seat reservation, enrollment, `PENDING` payment and outbox event go in **the same transaction**. If anything
fails (for example, the student doesn't exist), the `UPDATE` rolls back and the seat isn't lost.
`EnrollmentConcurrencyTest` fires 20 students at once at 3 seats and checks that exactly 3 get in.

## Request idempotency (`Idempotency-Key`)

`IdempotentRequests` reserves the key with `INSERT ... ON CONFLICT DO NOTHING` in the same transaction as the
enrollment:

- **First request:** inserts the key, enrolls and stores the response (the enrollment's id).
- **Retry with the same key and the same request:** returns the original enrollment without reserving another
  seat.
- **Same key, different request** (different student or course, compared by SHA-256 hash):
  `IdempotencyKeyReusedException` (422).
- **Two simultaneous retries:** the second blocks on the `INSERT` until the first commits, and then returns its
  result.
- **If the enrollment fails:** the key rolls back with it, so the client can retry with the same key.

Separately, the partial unique index on `enrollments` covers two concurrent requests from the same student *with
different keys*.

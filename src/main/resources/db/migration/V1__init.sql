-- Catalog
CREATE TABLE categories (
    id          UUID PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    status      VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_name UNIQUE (name)
);

CREATE TABLE instructors (
    id         UUID PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    bio        TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_instructors_email UNIQUE (email)
);

CREATE TABLE courses (
    id              UUID PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    duration_hours  INTEGER NOT NULL CHECK (duration_hours > 0),
    level           VARCHAR(20) NOT NULL CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    price           NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    capacity        INTEGER NOT NULL CHECK (capacity > 0),
    seats_taken     INTEGER NOT NULL DEFAULT 0 CHECK (seats_taken >= 0),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    category_id     UUID NOT NULL REFERENCES categories (id),
    instructor_id   UUID NOT NULL REFERENCES instructors (id),
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_courses_seats_within_capacity CHECK (seats_taken <= capacity)
);
CREATE INDEX idx_courses_category ON courses (category_id);
CREATE INDEX idx_courses_instructor ON courses (instructor_id);
CREATE INDEX idx_courses_status ON courses (status);
CREATE INDEX idx_courses_level ON courses (level);
CREATE INDEX idx_courses_price ON courses (price);

-- Students and enrollments
CREATE TABLE students (
    id         UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_students_email UNIQUE (email)
);

CREATE TABLE enrollments (
    id           UUID PRIMARY KEY,
    student_id   UUID NOT NULL REFERENCES students (id),
    course_id    UUID NOT NULL REFERENCES courses (id),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    progress     INTEGER NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    enrolled_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    version      BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_enrollments_student ON enrollments (student_id);
CREATE INDEX idx_enrollments_course ON enrollments (course_id);
-- A student can only hold one PENDING_PAYMENT/ACTIVE enrollment per course; CANCELLED/COMPLETED rows don't block re-enrollment.
CREATE UNIQUE INDEX uq_enrollments_active_per_student_course
    ON enrollments (student_id, course_id)
    WHERE status IN ('PENDING_PAYMENT', 'ACTIVE');

-- Payments
CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    enrollment_id   UUID NOT NULL REFERENCES enrollments (id),
    amount          NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3) NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED')),
    idempotency_key VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key)
);
CREATE INDEX idx_payments_enrollment ON payments (enrollment_id);

-- Certificates
CREATE TABLE certificates (
    id            UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL REFERENCES enrollments (id),
    code          VARCHAR(50) NOT NULL,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_certificates_enrollment UNIQUE (enrollment_id),
    CONSTRAINT uq_certificates_code UNIQUE (code)
);

-- Transactional outbox (reliable publication) and consumer-side dedup
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
CREATE INDEX idx_outbox_events_status_created ON outbox_events (status, created_at);

CREATE TABLE processed_events (
    event_id      UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);

-- HTTP-level idempotency (Idempotency-Key header on the enroll endpoint)
CREATE TABLE idempotency_keys (
    key             VARCHAR(100) PRIMARY KEY,
    endpoint        VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INTEGER,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

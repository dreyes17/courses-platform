CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'INSTRUCTOR', 'STUDENT')),
    student_id    UUID REFERENCES students (id),
    instructor_id UUID REFERENCES instructors (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_student UNIQUE (student_id),
    CONSTRAINT uq_users_instructor UNIQUE (instructor_id),
    -- Each role is linked to exactly the profile it acts as, and ADMIN to none.
    CONSTRAINT chk_users_role_profile CHECK (
        (role = 'STUDENT' AND student_id IS NOT NULL AND instructor_id IS NULL)
        OR (role = 'INSTRUCTOR' AND instructor_id IS NOT NULL AND student_id IS NULL)
        OR (role = 'ADMIN' AND student_id IS NULL AND instructor_id IS NULL)
    )
);

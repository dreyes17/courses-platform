package io.github.dreyes17.courses.enrollment.application;

import io.github.dreyes17.courses.enrollment.domain.Enrollment;
import io.github.dreyes17.courses.enrollment.domain.Student;
import io.github.dreyes17.courses.shared.application.MappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity → view only: views are built from entities, never the other way round (see docs/architecture.md, MapStruct). */
@Mapper(config = MappingConfig.class)
interface EnrollmentViewMapper {

    StudentView toView(Student student);

    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "courseId", source = "course.id")
    EnrollmentView toView(Enrollment enrollment);

    /** A student enrolled in a given course. */
    @Mapping(target = "enrollmentId", source = "id")
    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "firstName", source = "student.firstName")
    @Mapping(target = "lastName", source = "student.lastName")
    @Mapping(target = "email", source = "student.email")
    CourseEnrollmentView toCourseEnrollmentView(Enrollment enrollment);

    /** A course a given student is enrolled in. */
    @Mapping(target = "enrollmentId", source = "id")
    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "courseTitle", source = "course.title")
    StudentEnrollmentView toStudentEnrollmentView(Enrollment enrollment);

    /** Any enrollment, with both its student and its course. */
    @Mapping(target = "enrollmentId", source = "id")
    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "studentFirstName", source = "student.firstName")
    @Mapping(target = "studentLastName", source = "student.lastName")
    @Mapping(target = "studentEmail", source = "student.email")
    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "courseTitle", source = "course.title")
    EnrollmentSummaryView toSummaryView(Enrollment enrollment);
}

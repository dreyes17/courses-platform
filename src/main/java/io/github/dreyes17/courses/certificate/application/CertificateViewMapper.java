package io.github.dreyes17.courses.certificate.application;

import io.github.dreyes17.courses.certificate.domain.Certificate;
import io.github.dreyes17.courses.shared.application.MappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity → view only (see docs/architecture.md, MapStruct). */
@Mapper(config = MappingConfig.class)
interface CertificateViewMapper {

    @Mapping(target = "enrollmentId", source = "enrollment.id")
    @Mapping(target = "studentId", source = "enrollment.student.id")
    @Mapping(target = "courseId", source = "enrollment.course.id")
    @Mapping(target = "courseTitle", source = "enrollment.course.title")
    CertificateView toView(Certificate certificate);

    @Mapping(target = "holderName", expression =
            "java(certificate.getEnrollment().getStudent().getFirstName() + \" \" "
                    + "+ certificate.getEnrollment().getStudent().getLastName())")
    @Mapping(target = "courseTitle", source = "enrollment.course.title")
    CertificateVerification toVerification(Certificate certificate);
}

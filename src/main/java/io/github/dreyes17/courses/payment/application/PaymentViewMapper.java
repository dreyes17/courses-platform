package io.github.dreyes17.courses.payment.application;

import io.github.dreyes17.courses.payment.domain.Payment;
import io.github.dreyes17.courses.shared.application.MappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity → view only (see docs/architecture.md, MapStruct). */
@Mapper(config = MappingConfig.class)
interface PaymentViewMapper {

    @Mapping(target = "enrollmentId", source = "enrollment.id")
    PaymentView toView(Payment payment);
}

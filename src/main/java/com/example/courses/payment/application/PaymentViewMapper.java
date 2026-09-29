package com.example.courses.payment.application;

import com.example.courses.payment.domain.Payment;
import com.example.courses.shared.application.MappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity → view only (see README, MapStruct). */
@Mapper(config = MappingConfig.class)
interface PaymentViewMapper {

    @Mapping(target = "enrollmentId", source = "enrollment.id")
    PaymentView toView(Payment payment);
}

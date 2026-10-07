package io.github.dreyes17.courses.shared.application;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Shared settings for the entity → view mappers. With {@code unmappedTargetPolicy = ERROR}, adding a field to a
 * view without saying where its value comes from fails the build instead of silently returning null.
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MappingConfig {
}

package io.github.dreyes17.courses.catalog.application;

import io.github.dreyes17.courses.catalog.domain.Category;
import io.github.dreyes17.courses.catalog.domain.Course;
import io.github.dreyes17.courses.catalog.domain.Instructor;
import io.github.dreyes17.courses.shared.application.MappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entity → view only: views are built from entities, never the other way round (see docs/architecture.md, MapStruct). */
@Mapper(config = MappingConfig.class)
interface CatalogViewMapper {

    CategoryView toView(Category category);

    InstructorView toView(Instructor instructor);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "instructorId", source = "instructor.id")
    @Mapping(target = "instructorName", source = "instructor.name")
    // Explicit, because MapStruct would otherwise treat Course.hasAvailableSeats() as a presence check.
    @Mapping(target = "availableSeats", expression = "java(course.getAvailableSeats())")
    CourseView toView(Course course);
}

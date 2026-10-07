package io.github.dreyes17.courses.catalog.application;

import io.github.dreyes17.courses.catalog.domain.Category;
import io.github.dreyes17.courses.catalog.domain.CategoryStatus;
import io.github.dreyes17.courses.catalog.repository.CategoryRepository;
import io.github.dreyes17.courses.catalog.repository.CourseRepository;
import io.github.dreyes17.courses.shared.application.DuplicateResourceException;
import io.github.dreyes17.courses.shared.application.ResourceInUseException;
import io.github.dreyes17.courses.shared.application.ResourceNotFoundException;
import io.github.dreyes17.courses.shared.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final CourseRepository courses;
    private final CatalogViewMapper mapper;

    public CategoryService(CategoryRepository categories, CourseRepository courses, CatalogViewMapper mapper) {
        this.categories = categories;
        this.courses = courses;
        this.mapper = mapper;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CATEGORY_PAGES, allEntries = true)
    public CategoryView create(String name, String description) {
        if (categories.existsByName(name)) {
            throw new DuplicateResourceException("Category", "name", name);
        }
        return mapper.toView(categories.save(Category.create(name, description)));
    }

    /** Course views embed the category name, so they are evicted too. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CATEGORIES, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.CATEGORY_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.COURSES, allEntries = true)})
    public CategoryView update(UUID id, String name, String description) {
        Category category = find(id);
        if (categories.existsByNameAndIdNot(name, id)) {
            throw new DuplicateResourceException("Category", "name", name);
        }
        category.rename(name, description);
        return mapper.toView(category);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CATEGORIES, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.CATEGORY_PAGES, allEntries = true)})
    public CategoryView archive(UUID id) {
        Category category = find(id);
        category.archive();
        return mapper.toView(category);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CATEGORIES, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.CATEGORY_PAGES, allEntries = true)})
    public CategoryView activate(UUID id) {
        Category category = find(id);
        category.activate();
        return mapper.toView(category);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.CATEGORIES, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.CATEGORY_PAGES, allEntries = true)})
    public void delete(UUID id) {
        Category category = find(id);
        if (courses.existsByCategoryId(id)) {
            throw new ResourceInUseException("Category", id, "it still has courses; archive it instead");
        }
        categories.delete(category);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CATEGORIES, key = "#id")
    public CategoryView get(UUID id) {
        return mapper.toView(find(id));
    }

    /**
     * Only the unfiltered list is cached: it's the one clients read over and over, while filtered pages rarely
     * repeat and would only crowd it out.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CATEGORY_PAGES, key = "#pageable",
            condition = "#nameContains == null and #status == null")
    public Page<CategoryView> list(String nameContains, CategoryStatus status, Pageable pageable) {
        return categories.findAll(CategoryRepository.matching(nameContains, status), pageable).map(mapper::toView);
    }

    private Category find(UUID id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}

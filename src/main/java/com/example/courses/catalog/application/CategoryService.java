package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.Category;
import com.example.courses.catalog.repository.CategoryRepository;
import com.example.courses.catalog.repository.CourseRepository;
import com.example.courses.shared.application.DuplicateResourceException;
import com.example.courses.shared.application.ResourceInUseException;
import com.example.courses.shared.application.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final CourseRepository courses;

    public CategoryService(CategoryRepository categories, CourseRepository courses) {
        this.categories = categories;
        this.courses = courses;
    }

    @Transactional
    public CategoryView create(String name, String description) {
        if (categories.existsByName(name)) {
            throw new DuplicateResourceException("Category", "name", name);
        }
        return CategoryView.from(categories.save(Category.create(name, description)));
    }

    @Transactional
    public CategoryView update(UUID id, String name, String description) {
        Category category = find(id);
        if (categories.existsByNameAndIdNot(name, id)) {
            throw new DuplicateResourceException("Category", "name", name);
        }
        category.rename(name, description);
        return CategoryView.from(category);
    }

    @Transactional
    public CategoryView archive(UUID id) {
        Category category = find(id);
        category.archive();
        return CategoryView.from(category);
    }

    @Transactional
    public CategoryView activate(UUID id) {
        Category category = find(id);
        category.activate();
        return CategoryView.from(category);
    }

    @Transactional
    public void delete(UUID id) {
        Category category = find(id);
        if (courses.existsByCategoryId(id)) {
            throw new ResourceInUseException("Category", id, "it still has courses; archive it instead");
        }
        categories.delete(category);
    }

    @Transactional(readOnly = true)
    public CategoryView get(UUID id) {
        return CategoryView.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<CategoryView> list(Pageable pageable) {
        return categories.findAll(pageable).map(CategoryView::from);
    }

    private Category find(UUID id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}

package com.example.computer_store.core.service;

import com.example.computer_store.infrastructure.cache.CacheManager;
import com.example.computer_store.core.repository.CategoryRepository;
import com.example.computer_store.core.exception.NotFoundException;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.Category;
import com.example.computer_store.util.validation.ValidationUtil;

import java.util.List;

public class CategoryService {

    private static final String ALL_KEY = "all";

    private final CategoryRepository categoryDAO = new CategoryRepository();

    @SuppressWarnings("unchecked")
    public List<Category> getAll() {
        Object cached = CacheManager.getCategory(ALL_KEY);
        if (cached instanceof List) {
            return (List<Category>) cached;
        }
        List<Category> list = categoryDAO.findAll();
        CacheManager.putCategory(ALL_KEY, list);
        return list;
    }

    public Category get(int categoryId) {
        Category category = categoryDAO.findById(categoryId);
        if (category == null) {
            throw new NotFoundException("Category does not exist.");
        }
        return category;
    }

    public int create(String name, String description) {
        Category category = validate(0, name, description);
        int id = categoryDAO.create(category);
        CacheManager.invalidateAllCategories();
        CacheManager.invalidateAllCatalog(); // category tiles/counts on list pages
        return id;
    }

    public void update(int categoryId, String name, String description) {
        Category category = validate(categoryId, name, description);
        category.setCategoryId(categoryId);
        categoryDAO.update(category);
        CacheManager.invalidateAllCategories();
        CacheManager.invalidateAllCatalog(); // category names/counts on list pages
    }

    public void delete(int categoryId) {
        Category category = categoryDAO.findById(categoryId);
        if (category == null) {
            throw new NotFoundException("Category does not exist.");
        }
        // Count every product row (including soft-deleted) so we never hit FK errors.
        if (categoryDAO.countProducts(categoryId) > 0) {
            throw new ValidationException(
                    "Cannot delete a category that still contains products.");
        }
        if (!categoryDAO.delete(categoryId)) {
            throw new ValidationException("Category could not be deleted.");
        }
        CacheManager.invalidateAllCategories();
        CacheManager.invalidateAllCatalog(); // category tiles/counts on list pages
    }

    private Category validate(int categoryId, String name, String description) {
        if (ValidationUtil.isBlank(name)) {
            throw new ValidationException("Category name is required.");
        }
        Category existing = categoryDAO.findByName(name.trim());
        if (existing != null && existing.getCategoryId() != categoryId) {
            throw new ValidationException("A category with this name already exists.");
        }
        Category category = new Category();
        category.setName(name.trim());
        category.setDescription(ValidationUtil.isBlank(description) ? null : description.trim());
        return category;
    }
}
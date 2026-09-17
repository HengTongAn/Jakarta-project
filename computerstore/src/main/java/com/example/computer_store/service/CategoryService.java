package com.example.computer_store.service;

import com.example.computer_store.dao.CategoryDAO;
import com.example.computer_store.exception.NotFoundException;
import com.example.computer_store.exception.ValidationException;
import com.example.computer_store.model.Category;
import com.example.computer_store.util.ValidationUtil;

import java.util.List;

public class CategoryService {

    private final CategoryDAO categoryDAO = new CategoryDAO();

    public List<Category> getAll() {
        return categoryDAO.findAll();
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
        return categoryDAO.create(category);
    }

    public void update(int categoryId, String name, String description) {
        Category category = validate(categoryId, name, description);
        category.setCategoryId(categoryId);
        categoryDAO.update(category);
    }

    public void delete(int categoryId) {
        Category category = categoryDAO.findById(categoryId);
        if (category == null) {
            throw new NotFoundException("Category does not exist.");
        }
        if (category.getProductCount() > 0) {
            throw new ValidationException(
                    "Cannot delete a category that still contains products.");
        }
        if (!categoryDAO.delete(categoryId)) {
            throw new ValidationException("Category could not be deleted.");
        }
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
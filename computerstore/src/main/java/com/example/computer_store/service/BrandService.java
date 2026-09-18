package com.example.computer_store.service;

import com.example.computer_store.dao.BrandDAO;
import com.example.computer_store.exception.NotFoundException;
import com.example.computer_store.exception.ValidationException;
import com.example.computer_store.model.Brand;
import com.example.computer_store.util.ValidationUtil;

import java.util.List;

public class BrandService {

    private final BrandDAO brandDAO = new BrandDAO();

    public List<Brand> getAll() {
        return brandDAO.findAll();
    }

    public Brand get(int brandId) {
        Brand brand = brandDAO.findById(brandId);
        if (brand == null) {
            throw new NotFoundException("Brand does not exist.");
        }
        return brand;
    }

    public int create(String name, String description) {
        Brand brand = validate(0, name, description);
        return brandDAO.create(brand);
    }

    public void update(int brandId, String name, String description) {
        Brand brand = validate(brandId, name, description);
        brand.setBrandId(brandId);
        brandDAO.update(brand);
    }

    public void delete(int brandId) {
        Brand brand = brandDAO.findById(brandId);
        if (brand == null) {
            throw new NotFoundException("Brand does not exist.");
        }
        if (brand.getProductCount() > 0) {
            throw new ValidationException(
                    "Cannot delete a brand that still contains products.");
        }
        if (!brandDAO.delete(brandId)) {
            throw new ValidationException("Brand could not be deleted.");
        }
    }

    private Brand validate(int brandId, String name, String description) {
        if (ValidationUtil.isBlank(name)) {
            throw new ValidationException("Brand name is required.");
        }
        Brand existing = brandDAO.findByName(name.trim());
        if (existing != null && existing.getBrandId() != brandId) {
            throw new ValidationException("A brand with this name already exists.");
        }
        Brand brand = new Brand();
        brand.setName(name.trim());
        brand.setDescription(ValidationUtil.isBlank(description) ? null : description.trim());
        return brand;
    }
}
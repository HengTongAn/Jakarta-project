package com.example.computer_store.core.service;

import com.example.computer_store.infrastructure.cache.CacheManager;
import com.example.computer_store.core.repository.BrandRepository;
import com.example.computer_store.core.exception.NotFoundException;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.Brand;
import com.example.computer_store.util.validation.ValidationUtil;

import java.util.List;

public class BrandService {

    private static final String ALL_KEY = "all";

    private final BrandRepository brandDAO = new BrandRepository();

    @SuppressWarnings("unchecked")
    public List<Brand> getAll() {
        Object cached = CacheManager.getBrand(ALL_KEY);
        if (cached instanceof List) {
            return (List<Brand>) cached;
        }
        List<Brand> list = brandDAO.findAll();
        CacheManager.putBrand(ALL_KEY, list);
        return list;
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
        int id = brandDAO.create(brand);
        CacheManager.invalidateAllBrands();
        CacheManager.invalidateAllCatalog(); // brand names/counts on list pages
        return id;
    }

    public void update(int brandId, String name, String description) {
        Brand brand = validate(brandId, name, description);
        brand.setBrandId(brandId);
        brandDAO.update(brand);
        CacheManager.invalidateAllBrands();
        CacheManager.invalidateAllCatalog(); // brand names/counts on list pages
    }

    public void delete(int brandId) {
        Brand brand = brandDAO.findById(brandId);
        if (brand == null) {
            throw new NotFoundException("Brand does not exist.");
        }
        // Count every product row (including soft-deleted) so we never hit FK errors.
        if (brandDAO.countProducts(brandId) > 0) {
            throw new ValidationException(
                    "Cannot delete a brand that still contains products.");
        }
        if (!brandDAO.delete(brandId)) {
            throw new ValidationException("Brand could not be deleted.");
        }
        CacheManager.invalidateAllBrands();
        CacheManager.invalidateAllCatalog(); // brand names/counts on list pages
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
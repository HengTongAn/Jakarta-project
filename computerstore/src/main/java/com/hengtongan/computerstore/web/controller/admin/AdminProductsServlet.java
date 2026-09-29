package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.domain.entity.ProductSpec;
import com.hengtongan.computerstore.core.service.BrandService;
import com.hengtongan.computerstore.core.service.CategoryService;
import com.hengtongan.computerstore.core.service.ProductService;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.file.FileUploadUtil;
import com.hengtongan.computerstore.util.file.UploadConfig;
import com.hengtongan.computerstore.util.validation.ValidationUtil;
import com.hengtongan.computerstore.core.domain.dto.ProductFormVM;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin product management (explicit action routing):
 *   GET  /admin/products                         -> list
 *   GET  /admin/products?action=new              -> create form
 *   GET  /admin/products?action=edit&amp;id=ID   -> edit form
 *   POST /admin/products?action=delete&amp;id=ID -> delete / discontinue
 *   POST /admin/products                         -> create or update
 *
 * Legacy edit query parameters remain supported. Destructive actions are
 * intentionally POST-only so they are protected by the CSRF filter.
 */
@WebServlet("/admin/products")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize = 1024 * 1024 * 5,
    maxRequestSize = 1024 * 1024 * 10
)
public class AdminProductsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = resolveAction(request);
        switch (action) {
            case "new" -> showCreateForm(request, response);
            case "edit" -> showEditForm(request, response);
            case "delete" -> response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            default -> listProducts(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("delete".equals(resolveAction(request))) {
            deleteProduct(request, response);
        } else {
            saveProduct(request, response);
        }
    }

    private String resolveAction(HttpServletRequest request) {
        String action = request.getParameter("action");
        if (action != null && !action.isBlank()) {
            return action.trim().toLowerCase();
        }
        // Legacy query params
        if (request.getParameter("edit") != null) {
            return "edit";
        }
        if (request.getParameter("delete") != null) {
            return "delete";
        }
        if (request.getParameter("new") != null) {
            return "new";
        }
        return "list";
    }

    private Integer resolveId(HttpServletRequest request) {
        Integer id = ValidationUtil.parseInt(request.getParameter("id"));
        if (id != null) {
            return id;
        }
        id = ValidationUtil.parseInt(request.getParameter("edit"));
        if (id != null) {
            return id;
        }
        return ValidationUtil.parseInt(request.getParameter("delete"));
    }

    private void listProducts(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("products", productService().getAll());
        forward(request, response, "admin/products/list.jsp");
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        forwardForm(request, response, null);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer editId = resolveId(request);
        if (editId == null) {
            flashError(request, "Product id is required.");
            redirect(request, response, "/admin/products");
            return;
        }
        try {
            forwardForm(request, response, productService().get(editId), productService().getSpecs(editId));
        } catch (NotFoundException e) {
            flashError(request, e.getMessage());
            redirect(request, response, "/admin/products");
        }
    }

    private void deleteProduct(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Integer deleteId = resolveId(request);
        if (deleteId == null) {
            flashError(request, "Product id is required.");
            redirect(request, response, "/admin/products");
            return;
        }
        try {
            boolean deleted = productService().delete(deleteId);
            AuditLogger.logAdminAction(
                    deleted ? "PRODUCT_DELETE" : "PRODUCT_DISCONTINUE",
                    currentUsername(request), "product #" + deleteId,
                    deleted ? "Product removed (no order history)"
                            : "Product has order history, marked as DISCONTINUED instead");
            if (deleted) {
                flashSuccess(request, "Product deleted.");
            } else {
                flashSuccess(request, "Product has order history and was marked as DISCONTINUED instead.");
            }
        } catch (ValidationException e) {
            flashError(request, e.getMessage());
        }
        redirect(request, response, "/admin/products");
    }

    private void saveProduct(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer productId = ValidationUtil.parseInt(request.getParameter("productId"));
        Integer categoryId = ValidationUtil.parseInt(request.getParameter("categoryId"));
        Integer brandId = ValidationUtil.parseInt(request.getParameter("brandId"));
        String name = request.getParameter("name");
        String sku = request.getParameter("sku");
        String description = request.getParameter("description");
        BigDecimal price = ValidationUtil.parseDecimal(request.getParameter("price"));
        int stock = optionalInt(request.getParameter("stockQuantity"), 0);

        String highlights = request.getParameter("highlights");
        String boxContents = request.getParameter("boxContents");
        String warrantyInfo = request.getParameter("warrantyInfo");
        String sourceUrl = request.getParameter("sourceUrl");
        List<ProductSpec> specs = parseSpecs(request);

        String imageUrl = null;
        try {
            Product existing = productId == null ? null : productService().get(productId);
            Part filePart = request.getPart("productImage");
            String uploadPath = UploadConfig.getUploadBasePath();
            if (filePart != null && filePart.getSize() > 0) {
                imageUrl = FileUploadUtil.saveUploadedFile(filePart, uploadPath);
            }

            if (imageUrl == null && existing != null) {
                imageUrl = existing.getImageUrl();
            }

            if (productId == null) {
                productService().create(categoryId, brandId, name, sku, description, price, stock, imageUrl,
                        highlights, boxContents, warrantyInfo, sourceUrl, specs);
                AuditLogger.logAdminAction("PRODUCT_CREATE", currentUsername(request), "product",
                        "Created '" + name + "' (sku=" + sku + ")");
                flashSuccess(request, "Product created successfully.");
            } else {
                productService().update(productId, categoryId, brandId, name, sku, description, price, stock, imageUrl,
                        highlights, boxContents, warrantyInfo, sourceUrl, specs);
                if (existing != null && imageUrl != null && !imageUrl.equals(existing.getImageUrl())
                        && existing.getImageUrl() != null && !existing.getImageUrl().isBlank()) {
                    FileUploadUtil.deleteFile(existing.getImageUrl(), uploadPath);
                }
                AuditLogger.logAdminAction("PRODUCT_UPDATE", currentUsername(request), "product #" + productId,
                        "Updated '" + name + "'");
                flashSuccess(request, "Product updated successfully.");
            }
            redirect(request, response, "/admin/products");
        } catch (ValidationException | NotFoundException e) {
            cleanupNewImage(imageUrl, productId);
            request.setAttribute("error", e.getMessage());
            forwardForm(request, response, buildTemporaryProduct(request, productId), specs);
        } catch (IOException | ServletException | IllegalStateException e) {
            request.setAttribute("error", "Image upload failed: " + e.getMessage());
            forwardForm(request, response, buildTemporaryProduct(request, productId), specs);
        } catch (Exception e) {
            cleanupNewImage(imageUrl, productId);
            request.setAttribute("error", "An unexpected error occurred: " + e.getMessage());
            forwardForm(request, response, buildTemporaryProduct(request, productId), specs);
        }
    }

    private void cleanupNewImage(String imageUrl, Integer productId) {
        if (imageUrl == null) {
            return;
        }
        if (productId == null) {
            FileUploadUtil.deleteFile(imageUrl, UploadConfig.getUploadBasePath());
            return;
        }
        try {
            Product existing = productService().get(productId);
            if (!imageUrl.equals(existing.getImageUrl())) {
                FileUploadUtil.deleteFile(imageUrl, UploadConfig.getUploadBasePath());
            }
        } catch (RuntimeException ignored) {
            FileUploadUtil.deleteFile(imageUrl, UploadConfig.getUploadBasePath());
        }
    }

    private void forwardForm(HttpServletRequest request, HttpServletResponse response, Product product)
            throws ServletException, IOException {
        forwardForm(request, response, product, List.of());
    }

    private void forwardForm(HttpServletRequest request, HttpServletResponse response, Product product,
                             List<ProductSpec> specs)
            throws ServletException, IOException {
        CategoryService categoryService = app().categoryService();
        BrandService brandService = app().brandService();

        if (product != null) {
            ProductFormVM form = ProductFormVM.fromProduct(product, request.getContextPath(), specs);
            request.setAttribute("product", form);
            request.setAttribute("selectedCategoryId", form.getCategoryId());
            request.setAttribute("selectedBrandId", form.getBrandId());
            request.setAttribute("currentImageUrl", form.getCurrentImageUrl());
        }
        request.setAttribute("categories", categoryService.getAll());
        request.setAttribute("brands", brandService.getAll());
        forward(request, response, "admin/products/form.jsp");
    }

    private Product buildTemporaryProduct(HttpServletRequest request, Integer productId) {
        Product p = new Product();
        if (productId != null) {
            p.setProductId(productId);
        }
        p.setCategoryId(optionalInt(request.getParameter("categoryId"), 0));
        p.setBrandId(optionalInt(request.getParameter("brandId"), 0));
        p.setName(request.getParameter("name"));
        p.setSku(request.getParameter("sku"));
        p.setDescription(request.getParameter("description"));
        p.setPrice(ValidationUtil.parseDecimal(request.getParameter("price")));
        p.setStockQuantity(optionalInt(request.getParameter("stockQuantity"), 0));
        p.setHighlights(request.getParameter("highlights"));
        p.setBoxContents(request.getParameter("boxContents"));
        p.setWarrantyInfo(request.getParameter("warrantyInfo"));
        p.setSourceUrl(request.getParameter("sourceUrl"));
        return p;
    }

    /**
     * Reads the dynamic specification rows (name="specKey" / name="specValue").
     * Rows where both name and value are blank are dropped; ordering follows the
     * form order. Final cleaning/validation happens in the service layer.
     */
    private List<ProductSpec> parseSpecs(HttpServletRequest request) {
        List<ProductSpec> specs = new ArrayList<>();
        String[] keys = request.getParameterValues("specKey");
        String[] values = request.getParameterValues("specValue");
        if (keys == null) {
            return specs;
        }
        int sortOrder = 0;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i] == null ? "" : keys[i].trim();
            String value = (values != null && i < values.length && values[i] != null)
                    ? values[i].trim()
                    : "";
            if (key.isEmpty() && value.isEmpty()) {
                continue;
            }
            specs.add(new ProductSpec(0, 0, key, value, sortOrder++));
        }
        return specs;
    }

    private int optionalInt(String value, int defaultValue) {
        Integer parsed = ValidationUtil.parseInt(value);
        return parsed == null ? defaultValue : parsed;
    }

    private ProductService productService() {
        return app().productService();
    }
}

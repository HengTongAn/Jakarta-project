package com.example.computer_store.controller.customer;

import com.example.computer_store.controller.base.BaseServlet;
import com.example.computer_store.exception.NotFoundException;
import com.example.computer_store.model.Brand;
import com.example.computer_store.model.Category;
import com.example.computer_store.model.Product;
import com.example.computer_store.model.User;
import com.example.computer_store.service.BrandService;
import com.example.computer_store.service.CartService;
import com.example.computer_store.service.CategoryService;
import com.example.computer_store.service.ProductService;
import com.example.computer_store.util.ValidationUtil;
import com.example.computer_store.viewmodel.ActiveFilterVM;
import com.example.computer_store.viewmodel.ProductViewMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The catalogue page for customers.
 * GET /products               -> product list with search / filters
 * GET /products?id=5          -> one product detail page
 */
@WebServlet("/products")
public class ProductServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer productId = ValidationUtil.parseInt(request.getParameter("id"));
        if (productId != null) {
            showDetail(productId, request, response);
        } else {
            showList(request, response);
        }
    }

    private void showDetail(int productId, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ProductService productService = app().productService();
        CategoryService categoryService = app().categoryService();
        try {
            Product product = productService.get(productId);
            if (product.getStatus() == Product.Status.DISCONTINUED) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("product", ProductViewMapper.toDetail(product));
            request.setAttribute("categories", categoryService.getAll());
        } catch (NotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        forward(request, response, "customer/product-detail.jsp");
    }

    private void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ProductService productService = app().productService();
        CategoryService categoryService = app().categoryService();
        BrandService brandService = app().brandService();

        String search = request.getParameter("search");
        List<Integer> categoryIds = parseInts(request.getParameterValues("category"));
        List<Integer> brandIds = parseInts(request.getParameterValues("brand"));
        BigDecimal minPrice = ValidationUtil.parseDecimal(request.getParameter("minPrice"));
        BigDecimal maxPrice = ValidationUtil.parseDecimal(request.getParameter("maxPrice"));
        String sort = request.getParameter("sort");

        List<Product> products = productService.search(search, categoryIds, brandIds, minPrice, maxPrice, sort);
        List<Product> newArrivals = productService.search(null, List.of(), List.of(), null, null, "newest");

        request.setAttribute("products", ProductViewMapper.toCards(products));
        request.setAttribute("trending", ProductViewMapper.toCards(productService.findTrending(8)));
        request.setAttribute("newArrivals", ProductViewMapper.toCards(newArrivals.stream().limit(4).toList()));
        request.setAttribute("categories", categoryService.getAll());
        request.setAttribute("brands", brandService.getAll());
        request.setAttribute("categoryCounts", productService.countByCategory(search, brandIds, minPrice, maxPrice));
        request.setAttribute("brandCounts", productService.countByBrand(search, categoryIds, minPrice, maxPrice));
        request.setAttribute("cartCount", cartCount(request));
        request.setAttribute("selectedCategoryIds", categoryIds);
        request.setAttribute("selectedBrandIds", brandIds);
        request.setAttribute("sort", sort);
        request.setAttribute("activeFilters",
                buildActiveFilters(search, categoryIds, brandIds, minPrice, maxPrice, sort));

        forward(request, response, "customer/products.jsp");
    }

    private List<ActiveFilterVM> buildActiveFilters(String search, List<Integer> categoryIds,
                                                    List<Integer> brandIds, BigDecimal minPrice,
                                                    BigDecimal maxPrice, String sort) {
        CategoryService categoryService = app().categoryService();
        BrandService brandService = app().brandService();
        List<ActiveFilterVM> filters = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            filters.add(new ActiveFilterVM("Search: " + search.trim(),
                    buildUrl(null, categoryIds, brandIds, null, null, sort, null, null)));
        }

        List<Category> categories = categoryService.getAll();
        for (Integer c : categoryIds) {
            String name = "Category " + c;
            for (Category cat : categories) {
                if (cat.getCategoryId() == c) {
                    name = cat.getName();
                    break;
                }
            }
            filters.add(new ActiveFilterVM(name,
                    buildUrl(search, categoryIds, brandIds, minPrice, maxPrice, sort, c, null)));
        }

        List<Brand> brands = brandService.getAll();
        for (Integer b : brandIds) {
            String name = "Brand " + b;
            for (Brand br : brands) {
                if (br.getBrandId() == b) {
                    name = br.getName();
                    break;
                }
            }
            filters.add(new ActiveFilterVM(name,
                    buildUrl(search, categoryIds, brandIds, minPrice, maxPrice, sort, null, b)));
        }

        if (minPrice != null) {
            filters.add(new ActiveFilterVM("Min $" + minPrice.toPlainString(),
                    buildUrl(search, categoryIds, brandIds, null, maxPrice, sort, null, null)));
        }
        if (maxPrice != null) {
            filters.add(new ActiveFilterVM("Max $" + maxPrice.toPlainString(),
                    buildUrl(search, categoryIds, brandIds, minPrice, null, sort, null, null)));
        }

        return filters;
    }

    private String buildUrl(String search, List<Integer> categoryIds, List<Integer> brandIds,
                            BigDecimal minPrice, BigDecimal maxPrice, String sort,
                            Integer excludeCategory, Integer excludeBrand) {
        StringBuilder q = new StringBuilder();
        appendParam(q, "search", search);

        if (categoryIds != null) {
            for (Integer c : categoryIds) {
                if (excludeCategory == null || !c.equals(excludeCategory)) {
                    appendParam(q, "category", c.toString());
                }
            }
        }
        if (brandIds != null) {
            for (Integer b : brandIds) {
                if (excludeBrand == null || !b.equals(excludeBrand)) {
                    appendParam(q, "brand", b.toString());
                }
            }
        }

        appendParam(q, "minPrice", minPrice);
        appendParam(q, "maxPrice", maxPrice);
        appendParam(q, "sort", sort);

        return q.length() == 0 ? "/products" : "/products?" + q;
    }

    private void appendParam(StringBuilder q, String name, Object value) {
        if (value == null || value.toString().isEmpty()) {
            return;
        }
        if (q.length() > 0) {
            q.append("&");
        }
        q.append(name).append("=").append(encode(value.toString()));
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private List<Integer> parseInts(String[] values) {
        List<Integer> result = new ArrayList<>();
        if (values != null) {
            for (String v : values) {
                Integer n = ValidationUtil.parseInt(v);
                if (n != null && !result.contains(n)) {
                    result.add(n);
                }
            }
        }
        return result;
    }

    private int cartCount(HttpServletRequest request) {
        User user = currentUser(request);
        if (user == null) {
            return 0;
        }
        CartService cartService = app().cartService();
        return cartService.countItems(user.getUserId());
    }
}

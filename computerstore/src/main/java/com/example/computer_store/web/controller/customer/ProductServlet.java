package com.example.computer_store.web.controller.customer;

import com.example.computer_store.infrastructure.cache.CacheManager;
import com.example.computer_store.web.controller.base.BaseServlet;
import com.example.computer_store.core.exception.NotFoundException;
import com.example.computer_store.core.domain.entity.Brand;
import com.example.computer_store.core.domain.entity.Category;
import com.example.computer_store.core.domain.entity.Product;
import com.example.computer_store.core.domain.entity.RatingSummary;
import com.example.computer_store.core.domain.entity.Review;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.core.service.BrandService;
import com.example.computer_store.core.service.CategoryService;
import com.example.computer_store.core.service.ProductService;
import com.example.computer_store.core.service.ReviewService;
import com.example.computer_store.util.validation.ValidationUtil;
import com.example.computer_store.core.domain.dto.ActiveFilterVM;
import com.example.computer_store.core.domain.dto.ProductViewMapper;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The catalogue page for customers.
 * GET /products               -> product list with search / filters
 * GET /products?id=5          -> one product detail page
 */
@SuppressWarnings("unchecked")
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
        ReviewService reviewService = app().reviewService();
        try {
            Product product = productService.get(productId);
            if (product.getStatus() == Product.Status.DISCONTINUED) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("product", ProductViewMapper.toDetail(product, app().productService().getSpecs(productId)));
            request.setAttribute("categories", categoryService.getAll());
            // Customer reviews (approved only) + the aggregate rating.
            request.setAttribute("reviews", reviewService.findApprovedByProduct(productId));
            request.setAttribute("ratingSummary", reviewService.getRatingSummary(productId));
            User user = currentUser(request);
            if (user != null) {
                request.setAttribute("myReview", reviewService.findByUserAndProduct(user.getUserId(), productId));
            }
        } catch (NotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        forward(request, response, "customer/products/detail.jsp");
    }

    private void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        CategoryService categoryService = app().categoryService();
        BrandService brandService = app().brandService();

        String search = request.getParameter("search");
        List<Integer> categoryIds = parseInts(request.getParameterValues("category"));
        List<Integer> brandIds = parseInts(request.getParameterValues("brand"));
        BigDecimal minPrice = ValidationUtil.parseDecimal(request.getParameter("minPrice"));
        BigDecimal maxPrice = ValidationUtil.parseDecimal(request.getParameter("maxPrice"));
        String sort = request.getParameter("sort");

        // Paginated catalogue page - never pull the entire inventory into memory.
        // Keep the initial HTML and image work bounded on slower devices.
        final int pageSize = 24;
        Integer rawPage = ValidationUtil.parseInt(request.getParameter("page"));
        int page = Math.max(1, rawPage == null ? 1 : rawPage);

        // All of the reads behind a catalogue page are memoized as one block
        // (single-flight per search+filters+page fingerprint); a burst of
        // concurrent clicks on the same page shares one database load instead
        // of each request re-running ~9 queries against the connection pool.
        Map<String, Object> data = loadCatalog(search, categoryIds, brandIds,
                minPrice, maxPrice, sort, page, pageSize);
        @SuppressWarnings("unchecked")
        List<Product> products = (List<Product>) data.get("products");
        long totalMatches = (Long) data.get("totalMatches");
        int totalPages = (int) data.get("totalPages");
        page = (int) data.get("page");
        @SuppressWarnings("unchecked")
        List<Product> newArrivals = (List<Product>) data.get("newArrivals");
        @SuppressWarnings("unchecked")
        List<Product> trending = (List<Product>) data.get("trending");
        @SuppressWarnings("unchecked")
        Map<Integer, RatingSummary> summaries = (Map<Integer, RatingSummary>) data.get("summaries");
        @SuppressWarnings("unchecked")
        Map<Integer, Long> categoryCounts = (Map<Integer, Long>) data.get("categoryCounts");
        @SuppressWarnings("unchecked")
        Map<Integer, Long> brandCounts = (Map<Integer, Long>) data.get("brandCounts");
        @SuppressWarnings("unchecked")
        List<Review> recentReviews = (List<Review>) data.get("recentReviews");

        List<Category> categories = categoryService.getAll();
        List<Brand> brands = brandService.getAll();

        request.setAttribute("products", ProductViewMapper.toCards(products, summaries));
        request.setAttribute("trending", ProductViewMapper.toCards(trending, summaries));
        request.setAttribute("newArrivals", ProductViewMapper.toCards(newArrivals, summaries));
        request.setAttribute("categories", categories);
        request.setAttribute("brands", brands);
        request.setAttribute("categoryCounts", categoryCounts);
        request.setAttribute("brandCounts", brandCounts);
        // cartCount is set by CartCountFilter (session-cached); do not re-query here
        request.setAttribute("selectedCategoryIds", categoryIds);
        request.setAttribute("selectedBrandIds", brandIds);
        request.setAttribute("sort", sort);
        request.setAttribute("page", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalMatches", totalMatches);
        List<ActiveFilterVM> activeFilters =
                buildActiveFilters(search, categoryIds, brandIds, minPrice, maxPrice, sort,
                        categories, brands);
        request.setAttribute("activeFilters", activeFilters);
        // Storefront home (no active filters) shows the latest approved reviews;
        // the JSP keeps the static testimonials as its empty-state fallback.
        request.setAttribute("recentReviews", recentReviews);

        forward(request, response, "customer/products/catalog.jsp");
    }

    /**
     * All of the reads behind a catalogue page, loaded as one memoized block.
     * Concurrent requests with the same fingerprint (search + filters + page)
     * share a single database load via the single-flight catalog cache; the
     * 60 s TTL is the staleness backstop, and every admin write that can change
     * what a list page shows busts the memo immediately
     * ({@link CacheManager#invalidateAllCatalog()}).
     */
    private Map<String, Object> loadCatalog(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                            BigDecimal minPrice, BigDecimal maxPrice, String sort,
                                            int page, int pageSize) {
        final String key = "catalog:" + catalogKey(search, categoryIds, brandIds,
                minPrice, maxPrice, sort, page);
        if (CacheManager.isCacheEnabled()) {
            Object memo = CacheManager.getOrLoadCatalog(key,
                    k -> buildCatalog(search, categoryIds, brandIds, minPrice, maxPrice, sort, page, pageSize));
            return (Map<String, Object>) memo;
        }
        return buildCatalog(search, categoryIds, brandIds, minPrice, maxPrice, sort, page, pageSize);
    }

    /** The database-heavy half of a catalogue page (runs once per fingerprint). */
    private Map<String, Object> buildCatalog(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                             BigDecimal minPrice, BigDecimal maxPrice, String sort,
                                             int page, int pageSize) {
        ProductService productService = app().productService();
        int offset = (page - 1) * pageSize;
        List<Product> products = productService.search(
                search, categoryIds, brandIds, minPrice, maxPrice, sort, pageSize, offset);
        long totalMatches = productService.countSearch(search, categoryIds, brandIds, minPrice, maxPrice);
        int totalPages = (int) Math.max(1, (totalMatches + pageSize - 1) / pageSize);
        if (page > totalPages) {
            page = totalPages;
            offset = (page - 1) * pageSize;
            products = productService.search(
                    search, categoryIds, brandIds, minPrice, maxPrice, sort, pageSize, offset);
        }
        // New arrivals: SQL LIMIT 4 instead of loading every product then slicing.
        List<Product> newArrivals = productService.search(
                null, List.of(), List.of(), null, null, "newest", 4);
        List<Product> trending = productService.findTrending(8);
        // One aggregate query for every card on the page (no per-product query).
        Map<Integer, RatingSummary> summaries = ratingSummaries(products, trending, newArrivals);
        Map<Integer, Long> categoryCounts =
                productService.countByCategory(search, brandIds, minPrice, maxPrice);
        Map<Integer, Long> brandCounts =
                productService.countByBrand(search, categoryIds, minPrice, maxPrice);
        List<Review> recentReviews = hasActiveFilters(search, categoryIds, brandIds, minPrice, maxPrice)
                ? List.of() : app().reviewService().recentApproved(3);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("products", products);
        data.put("totalMatches", totalMatches);
        data.put("totalPages", totalPages);
        data.put("page", page);
        data.put("newArrivals", newArrivals);
        data.put("trending", trending);
        data.put("summaries", summaries);
        data.put("categoryCounts", categoryCounts);
        data.put("brandCounts", brandCounts);
        data.put("recentReviews", recentReviews);
        return data;
    }

    /**
     * Canonical fingerprint of a catalogue page so the memo is shared by
     * identical URL hits. Category/brand ids are sorted so
     * {@code ?brand=2&brand=1} and {@code ?brand=1&brand=2} collapse to one
     * entry; search is case-insensitive to match the DB's collation.
     */
    private static String catalogKey(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                     BigDecimal minPrice, BigDecimal maxPrice, String sort, int page) {
        StringBuilder sb = new StringBuilder();
        sb.append(page).append('|');
        if (search != null && !search.trim().isEmpty()) {
            sb.append(search.trim().toLowerCase());
        }
        sb.append('|');
        if (categoryIds != null) {
            List<Integer> sorted = new ArrayList<>(categoryIds);
            sorted.sort(Integer::compareTo);
            for (Integer c : sorted) {
                sb.append(c).append(',');
            }
        }
        sb.append('|');
        if (brandIds != null) {
            List<Integer> sorted = new ArrayList<>(brandIds);
            sorted.sort(Integer::compareTo);
            for (Integer b : sorted) {
                sb.append(b).append(',');
            }
        }
        sb.append('|');
        if (minPrice != null) {
            sb.append(minPrice.toPlainString());
        }
        sb.append('|');
        if (maxPrice != null) {
            sb.append(maxPrice.toPlainString());
        }
        sb.append('|');
        if (sort != null) {
            sb.append(sort);
        }
        return sb.toString();
    }

    /** Mirrors {@link #buildActiveFilters}: a page is "filtered" when any of these are set. */
    private static boolean hasActiveFilters(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                            BigDecimal minPrice, BigDecimal maxPrice) {
        return (search != null && !search.trim().isEmpty())
                || (categoryIds != null && !categoryIds.isEmpty())
                || (brandIds != null && !brandIds.isEmpty())
                || minPrice != null
                || maxPrice != null;
    }

    /** One aggregate query for the ratings of every card shown on the page. */
    private Map<Integer, RatingSummary> ratingSummaries(List<Product>... lists) {
        Set<Integer> productIds = new HashSet<>();
        for (List<Product> list : lists) {
            if (list == null) {
                continue;
            }
            for (Product product : list) {
                if (product != null) {
                    productIds.add(product.getProductId());
                }
            }
        }
        return app().reviewService().getRatingSummaries(productIds);
    }

    private List<ActiveFilterVM> buildActiveFilters(String search, List<Integer> categoryIds,
                                                    List<Integer> brandIds, BigDecimal minPrice,
                                                    BigDecimal maxPrice, String sort,
                                                    List<Category> categories, List<Brand> brands) {
        List<ActiveFilterVM> filters = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            filters.add(new ActiveFilterVM("Search: " + search.trim(),
                    buildUrl(null, categoryIds, brandIds, null, null, sort, null, null)));
        }

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
}

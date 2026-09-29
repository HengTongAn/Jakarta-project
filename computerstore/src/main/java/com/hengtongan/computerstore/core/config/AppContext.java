package com.hengtongan.computerstore.core.config;

import com.hengtongan.computerstore.core.service.AuthService;
import com.hengtongan.computerstore.core.service.AuditLogService;
import com.hengtongan.computerstore.core.service.BrandService;
import com.hengtongan.computerstore.core.service.CartService;
import com.hengtongan.computerstore.core.service.CategoryService;
import com.hengtongan.computerstore.core.service.DashboardService;
import com.hengtongan.computerstore.core.service.InventoryService;
import com.hengtongan.computerstore.core.service.MailService;
import com.hengtongan.computerstore.core.service.OrderService;
import com.hengtongan.computerstore.core.service.PasswordResetService;
import com.hengtongan.computerstore.core.service.ProductService;
import com.hengtongan.computerstore.core.service.ReportService;
import com.hengtongan.computerstore.core.service.ReviewService;
import com.hengtongan.computerstore.core.service.UserService;

/**
 * Application-wide service registry. Initialized once at startup by
 * {@link AppContextListener}. Controllers and filters obtain services here
 * instead of constructing them with {@code new}.
 */
public final class AppContext {

    private static volatile AppContext instance;

    private final ProductService productService;
    private final OrderService orderService;
    private final AuthService authService;
    private final CartService cartService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final UserService userService;
    private final MailService mailService;
    private final DashboardService dashboardService;
    private final InventoryService inventoryService;
    private final AuditLogService auditLogService;
    private final ReportService reportService;
    private final PasswordResetService passwordResetService;
    private final ReviewService reviewService;

    private AppContext() {
        this.productService = new ProductService();
        this.orderService = new OrderService();
        this.authService = new AuthService();
        this.cartService = new CartService();
        this.categoryService = new CategoryService();
        this.brandService = new BrandService();
        this.userService = new UserService();
        this.mailService = new MailService();
        this.dashboardService = new DashboardService();
        this.inventoryService = new InventoryService();
        this.auditLogService = new AuditLogService();
        this.reportService = new ReportService();
        this.passwordResetService = new PasswordResetService();
        this.reviewService = new ReviewService();
    }

    public static synchronized void init() {
        if (instance == null) {
            instance = new AppContext();
        }
    }

    /** For tests only. */
    public static synchronized void resetForTests(AppContext context) {
        instance = context;
    }

    public static AppContext get() {
        AppContext ctx = instance;
        if (ctx == null) {
            throw new IllegalStateException("AppContext not initialized. Is AppContextListener registered?");
        }
        return ctx;
    }

    public ProductService productService() {
        return productService;
    }

    public OrderService orderService() {
        return orderService;
    }

    public AuthService authService() {
        return authService;
    }

    public CartService cartService() {
        return cartService;
    }

    public CategoryService categoryService() {
        return categoryService;
    }

    public BrandService brandService() {
        return brandService;
    }

    public UserService userService() {
        return userService;
    }

    public MailService mailService() {
        return mailService;
    }

    public DashboardService dashboardService() {
        return dashboardService;
    }

    public InventoryService inventoryService() {
        return inventoryService;
    }

    public AuditLogService auditLogService() {
        return auditLogService;
    }

    public ReportService reportService() {
        return reportService;
    }

    public PasswordResetService passwordResetService() {
        return passwordResetService;
    }

    public ReviewService reviewService() {
        return reviewService;
    }
}

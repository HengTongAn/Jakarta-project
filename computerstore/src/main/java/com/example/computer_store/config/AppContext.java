package com.example.computer_store.config;

import com.example.computer_store.service.AuthService;
import com.example.computer_store.service.AuditLogService;
import com.example.computer_store.service.BrandService;
import com.example.computer_store.service.CartService;
import com.example.computer_store.service.CategoryService;
import com.example.computer_store.service.DashboardService;
import com.example.computer_store.service.InventoryService;
import com.example.computer_store.service.MailService;
import com.example.computer_store.service.OrderService;
import com.example.computer_store.service.ProductService;
import com.example.computer_store.service.ReportService;
import com.example.computer_store.service.UserService;
import com.example.computer_store.service.impl.AuthServiceImpl;
import com.example.computer_store.service.impl.CartServiceImpl;
import com.example.computer_store.service.impl.OrderServiceImpl;
import com.example.computer_store.service.impl.ProductServiceImpl;

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

    private AppContext() {
        this.productService = new ProductServiceImpl();
        this.orderService = new OrderServiceImpl();
        this.authService = new AuthServiceImpl();
        this.cartService = new CartServiceImpl();
        this.categoryService = new CategoryService();
        this.brandService = new BrandService();
        this.userService = new UserService();
        this.mailService = new MailService();
        this.dashboardService = new DashboardService();
        this.inventoryService = new InventoryService();
        this.auditLogService = new AuditLogService();
        this.reportService = new ReportService();
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
}

package com.hengtongan.computerstore.core.config;

import com.hengtongan.computerstore.core.service.AuthService;
import com.hengtongan.computerstore.core.service.AuditLogService;
import com.hengtongan.computerstore.core.service.BrandService;
import com.hengtongan.computerstore.core.service.CartService;
import com.hengtongan.computerstore.core.service.CategoryService;
import com.hengtongan.computerstore.core.service.DashboardService;
import com.hengtongan.computerstore.core.service.InventoryService;
import com.hengtongan.computerstore.core.service.OrderService;
import com.hengtongan.computerstore.core.service.PasswordResetService;
import com.hengtongan.computerstore.core.service.NotificationService;
import com.hengtongan.computerstore.core.service.ProductService;
import com.hengtongan.computerstore.core.service.ReportService;
import com.hengtongan.computerstore.core.service.ReviewService;
import com.hengtongan.computerstore.core.service.PageExperienceService;
import com.hengtongan.computerstore.core.service.PaymentService;
import com.hengtongan.computerstore.core.service.SupportChannelService;
import com.hengtongan.computerstore.core.service.TransactionService;
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
    private final DashboardService dashboardService;
    private final InventoryService inventoryService;
    private final AuditLogService auditLogService;
    private final ReportService reportService;
    private final ReviewService reviewService;
    private final PageExperienceService pageExperienceService;
    private final SupportChannelService supportChannelService;
    private final PaymentService paymentService;
    private final TransactionService transactionService;
    private final PasswordResetService passwordResetService;
    private final NotificationService notificationService;

    private AppContext() {
        this.productService = new ProductService();
        this.orderService = new OrderService();
        this.authService = new AuthService();
        this.cartService = new CartService();
        this.categoryService = new CategoryService();
        this.brandService = new BrandService();
        this.userService = new UserService();
        this.dashboardService = new DashboardService();
        this.inventoryService = new InventoryService();
        this.auditLogService = new AuditLogService();
        this.reportService = new ReportService();
        this.reviewService = new ReviewService();
        this.pageExperienceService = new PageExperienceService();
        this.supportChannelService = new SupportChannelService();
        this.paymentService = new PaymentService();
        this.transactionService = new TransactionService();
        this.passwordResetService = new PasswordResetService();
        // Shares the UserService instance above: sendToCustomer looks the
        // recipient up per order, and a second UserService would mean a second
        // cache and a second pool's worth of lookups for the same rows.
        this.notificationService = new NotificationService(userService);
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

    public ReviewService reviewService() {
        return reviewService;
    }

    public PageExperienceService pageExperienceService() {
        return pageExperienceService;
    }

    public SupportChannelService supportChannelService() {
        return supportChannelService;
    }

    public PaymentService paymentService() {
        return paymentService;
    }

    public TransactionService transactionService() {
        return transactionService;
    }

    public PasswordResetService passwordResetService() {
        return passwordResetService;
    }

    /**
     * Order-lifecycle email. This was written but never wired to anything: no
     * code constructed the class, so the "order placed" and "status changed"
     * mails it describes were never sent. Callers invoke it only after the
     * transaction has committed, and it never throws, so a mail problem cannot
     * undo an order.
     */
    public NotificationService notificationService() {
        return notificationService;
    }
}

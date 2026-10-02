# ComputerStore Codebase Documentation

## Project Overview
A Jakarta EE 11 web application for a computer e-commerce store built with Java 17, Maven, and MySQL. Features include product catalog management, shopping cart, order processing, payment integration (ABA Payway), admin dashboard, and user authentication with 2FA support.

## Technology Stack

### Core Framework
- **Jakarta EE 11** (Servlet 6.1, JSP 4.0)
- **Java 17** (Maven compiler release 17)
- **Tomcat 11** (embedded via Cargo plugin for development)

### Database
- **MySQL 9.6.0** (mysql-connector-j)
- **HikariCP 5.1.0** (connection pooling)
- Schema migrations in `src/main/resources/db/migrations/`

### Build & Deployment
- **Maven** (WAR packaging)
- **Cargo Maven Plugin 1.10.13** (embedded Tomcat 11)
- Run with: `mvn cargo:run` (runs on http://localhost:8081/computerstore)

### Key Libraries
- **Caffeine 3.1.8** (in-memory caching)
- **BCrypt 0.4** (password hashing)
- **Google Authenticator 1.5.0** (TOTP 2FA)
- **ZXing 3.5.2** (QR code generation)
- **Jakarta Mail 2.0.1** (email notifications)
- **SLF4J + Logback 1.5.6** (logging)
- **JUnit 5.10.2 + Mockito 5.12.0** (testing)

## Architecture

### Layer Structure
```
com.hengtongan.computerstore/
├── core/                 # Business logic layer
│   ├── config/          # AppContext, AppConfig, PaymentConfig
│   ├── domain/
│   │   ├── entity/      # JPA-like entities (Product, User, Order, etc.)
│   │   └── dto/         # View models (ProductCardVM, ProductDetailVM, etc.)
│   ├── repository/      # Data access layer (ProductRepository, UserRepository, etc.)
│   ├── service/         # Business services (ProductService, OrderService, etc.)
│   └── exception/       # Custom exceptions (NotFoundException, ValidationException)
├── infrastructure/      # Cross-cutting concerns
│   ├── cache/          # CacheManager (Caffeine-based)
│   ├── persistence/    # DBConnection, DatabaseMigrationRunner, SchemaUtil
│   ├── monitoring/     # MetricsCollector, QueryMonitor
│   ├── messaging/      # EmailUtil
│   ├── payment/        # AbaPaywayClient
│   ├── realtime/       # EventHub, RealtimeStreamServlet (SSE)
│   └── security/       # TwoFactorAuthService
├── util/               # Utilities
│   ├── cache/         # CountCache
│   ├── file/          # FileUploadUtil, UploadConfig
│   ├── json/          # MiniJsonParser
│   ├── sql/           # SqlUtil
│   ├── validation/    # ValidationUtil
│   └── web/           # ErrorHandler, AuditLogger
└── web/               # Web layer
    ├── controller/
    │   ├── admin/     # Admin servlets (products, orders, users, etc.)
    │   ├── auth/      # Login, register, password reset
    │   ├── customer/  # Cart, checkout, account pages
    │   ├── base/      # BaseServlet
    │   ├── error/     # Error handling servlets
    │   └── monitoring/# Health, metrics servlets
    └── filter/
        ├── security/  # Authentication, CSRF, rate limiting, etc.
        ├── monitoring/# RequestAuditContextFilter
        ├── performance/# Compression, static resource caching
        └── web/       # CartCountFilter, ReviewCountFilter, etc.
```

### Key Patterns

1. **Service Registry Pattern**: `AppContext` provides singleton access to all services
2. **Repository Pattern**: Repository classes handle data access with raw JDBC
3. **Filter Chain**: 15 filters handle security, caching, compression, etc. (in web.xml)
4. **Caching Strategy**: Caffeine caches with single-flight loading and cache invalidation on writes
5. **DTO Pattern**: View models separate presentation from domain entities
6. **Transaction Management**: Manual transaction handling in services with try/catch/rollback

### Database Schema
- Schema defined in `src/main/resources/db/schema.sql`
- Migrations in `src/main/resources/db/migrations/`
- Seed data in `src/main/resources/db/seed/`
- Connection via environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

### Configuration
- Environment variables loaded via `AppConfig` (priority: env var > system property > config file)
- Example in `.env.example` (copy to `.env` for local development)
- Key configs:
  - Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_MAX`, `DB_POOL_MIN`
  - Email: `MAIL_SMTP_HOST`, `MAIL_SMTP_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
  - 2FA: `COMPUTERSTORE_2FA_ENCRYPTION_KEY`
  - Uploads: `COMPUTERSTORE_UPLOAD_DIR`

## Build & Run Commands

### Development
```bash
# Load environment variables (zsh)
set -a; source .env; set +a

# Run with embedded Tomcat
mvn cargo:run

# Build WAR
mvn clean package

# Run tests
mvn test
```

### Database Setup
```bash
# Ensure MySQL is running and database exists
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS computer_store;"

# Migrations run automatically via DatabaseMigrationRunner on startup
```

## Key Features

### Admin Features

#### Dashboard (`/admin`, `/admin/`)
- View live statistics (total products, customers, orders, revenue, low stock, out of stock, pending orders, pending reviews)
- View recent orders list
- View recent inventory logs
- JSON endpoint for live SSE updates (`?json=1`)

#### Product Management (`/admin/products`)
- **GET /admin/products** - List all products
- **GET /admin/products?action=new** - Show create product form
- **GET /admin/products?action=edit&id=ID** - Show edit product form
- **POST /admin/products** - Create or update product (with image upload, specifications, highlights, box contents, warranty info, source URL)
- **POST /admin/products?action=delete&id=ID** - Delete or discontinue product (deletes if no order history, otherwise marks as DISCONTINUED)

#### Order Management (`/admin/orders`)
- **GET /admin/orders** - List orders (paginated, filterable by status)
- **GET /admin/orders?id=ID** - View order detail
- **POST /admin/orders** - Update order status (PENDING, PROCESSING, SHIPPED, COMPLETED, CANCELLED, REFUNDED)

#### User Management (`/admin/users`)
- **GET /admin/users** - List all users with online/offline status
- **GET /admin/users?action=new** - Show create user form
- **GET /admin/users?action=edit&id=ID** - Show edit user form
- **GET /admin/users?action=reset&id=ID** - Show password reset form
- **GET /admin/users?action=profile&id=ID** - View user profile with activity status
- **POST /admin/users?action=create** - Create user account (with avatar upload, role assignment)
- **POST /admin/users?action=update** - Update user profile (with avatar upload, role change)
- **POST /admin/users?action=reset** - Reset user password (admin override, no old password required)

#### Brand Management (`/admin/brands`)
- **GET /admin/brands** - List all brands
- **GET /admin/brands?edit=ID** - Show edit brand form (inline)
- **POST /admin/brands** - Create or update brand
- **POST /admin/brands?action=delete** - Delete brand

#### Category Management (`/admin/categories`)
- **GET /admin/categories** - List all categories
- **GET /admin/categories?edit=ID** - Show edit category form (inline)
- **POST /admin/categories** - Create or update category
- **POST /admin/categories?action=delete** - Delete category

#### Inventory Management (`/admin/inventory`)
- **GET /admin/inventory** - View stock overview, low stock items, out of stock items, recent inventory logs
- **POST /admin/inventory** - Manual stock adjustment (delta + reason: Received, Damaged, Counted, Return, Correction)

#### Review Moderation (`/admin/reviews`)
- **GET /admin/reviews** - List reviews (paginated, filterable by status: PENDING, APPROVED, REJECTED)
- **POST /admin/reviews?action=approve** - Approve review
- **POST /admin/reviews?action=reject** - Reject review
- **POST /admin/reviews?action=delete** - Delete review

#### Payment Configuration (`/admin/payments`)
- **GET /admin/payments** - View ABA Payway configuration form
- **POST /admin/payments** - Save payment settings (enable/disable, simulate mode, merchant ID, API URL, username, secret, shop name, currency)
- **POST /admin/payments?action=test** - Run connection test to ABA gateway (diagnoses reachability, HTTP status, latency)

#### Transaction Management (`/admin/transactions`)
- **GET /admin/transactions** - List all transactions (paginated, filterable by status and payment method)
- **GET /admin/transactions?id=ID** - View transaction detail
- **GET /admin/transactions?orderId=X** - View transactions for specific order
- **POST /admin/transactions?action=refund** - Refund a settled payment (whole refund, ledger-only, order marked REFUNDED with stock returned)

#### Reports (`/admin/reports`)
- **GET /admin/reports** - View sales reports (time range: today, 7d, 30d, all time)
  - Summary: total orders, revenue, items sold, avg order value, status breakdown
  - Revenue series chart
  - Top selling products
  - Low stock values
- **GET /admin/reports?json=1** - JSON endpoint for live SSE updates

#### Support Channel Configuration (`/admin/support`)
- **GET /admin/support** - View contact support channel settings form
- **POST /admin/support** - Save support channel URLs (email, phone, facebook, telegram, whatsapp, etc.)

#### Audit History (`/admin/history`)
- **GET /admin/history** - View audit log entries (paginated, searchable by keyword, type, actor)
- **GET /admin/history?view=alerts** - View suspicious pattern alerts
- **GET /admin/history?view=archive** - View archived audit logs
- **POST /admin/history?action=export** - Export audit logs to CSV
- **POST /admin/history?action=archive** - Archive audit logs older than specified retention period (365, 730, or 2555 days)

#### Performance Monitoring (`/admin/performance`)
- **GET /admin/performance** - View performance metrics
  - Cache statistics (hit rates, sizes for products, catalog, details, categories, brands, users, carts, orders, dashboard)
  - HikariCP connection pool state (active, idle, waiting, total, max, min)
  - JVM memory statistics
  - Top query execution times
  - Performance recommendations
- **GET /admin/performance?json=1** - JSON endpoint for live SSE updates

### Customer Features

#### Product Catalog (`/products`)
- **GET /products** - Product list with search and filters
  - Search by name, SKU, brand, description
  - Filter by category (multiple)
  - Filter by brand (multiple)
  - Filter by price range (min/max)
  - Sort by name, price (asc/desc), newest
  - Pagination (24 items per page)
  - View trending products, new arrivals
  - View category/brand counts (filtered)
  - View recent approved reviews
- **GET /products?id=ID** - Product detail page
  - Product information, specifications
  - Approved reviews with rating summary
  - User's own review (if logged in)

#### Shopping Cart (`/cart`, `/cart/add`, `/cart/update`, `/cart/remove`)
- **GET /cart** - View cart items and total
- **POST /cart/add** - Add item to cart (productId, quantity)
- **POST /cart/update** - Update cart item quantity (cartItemId, quantity)
- **POST /cart/remove** - Remove item from cart (cartItemId)
- AJAX support for cart operations

#### Checkout (`/checkout`)
- **GET /checkout** - View checkout page with cart items and total
  - Show available payment methods (ABA Payway, Card)
  - Show simulation mode indicators
  - Show test card numbers for card simulation
- **POST /checkout** - Place order and initiate payment
  - Validate payment method availability
  - For ABA: create order, redirect to ABA payment page
  - For Card: validate card details, authorize inline, show receipt on success

#### ABA Payment (`/payment/aba`)
- **GET /payment/aba?order=N** - View ABA QR code payment page
- **POST /payment/aba?order=N** - Demo controls (simulate mode only: simulate_paid, simulate_cancelled)
- **GET /payment/aba/return?order=N** - Gateway return endpoint (re-verifies with gateway, marks order paid, redirects to receipt)

#### Card Payment (`/payment/card`)
- **GET /payment/card?order=N** - View card payment status page (read-only)
  - Shows payment outcome, receipt link, retry option

#### Order History (`/account/orders`)
- **GET /account/orders** - List customer's orders
- **GET /account/orders?id=ID** - View order detail
- **POST /account/orders?action=cancel** - Cancel pending order (returns items to stock)

#### Transaction History (`/account/transactions`)
- **GET /account/transactions** - List customer's transactions (payments and refunds)
- **GET /account/transactions?id=ID** - View transaction detail
- **GET /account/transactions?id=ID&view=receipt** - View printable receipt

#### Profile Management (`/account/profile`)
- **GET /account/profile** - View profile form
- **POST /account/profile** - Update full name and email

#### Avatar Management (`/account/avatar`)
- **POST /account/avatar** - Upload avatar image

#### Security Settings (`/account/settings`)
- **GET /account/settings** - View password change form
- **POST /account/settings** - Change password (requires current password)

#### Two-Factor Authentication (`/account/2fa`)
- **GET /account/2fa** - View 2FA setup page
- **POST /account/2fa?action=generate** - Generate new TOTP secret key and QR code (only if 2FA disabled)
- **POST /account/2fa?action=enable** - Enable 2FA after verifying TOTP code
- **POST /account/2fa?action=disable** - Disable 2FA (requires valid TOTP code)

#### Product Reviews (`/products/review`)
- **POST /products/review** - Submit product review (rating, title, review text) - creates PENDING review awaiting admin approval

### Authentication Features

#### Login (`/login`)
- **GET /login** - View login form
- **POST /login** - Authenticate with username and password
  - Session rotation on successful login
  - TOTP 2FA verification if enabled
  - Safe return path handling (prevents open redirect)
- **POST /login?action=verify2fa** - Complete 2FA login with TOTP code

#### Registration (`/register`)
- **GET /register** - View registration form
- **POST /register** - Create new customer account (username, full name, email, password, confirm password)
  - Session rotation on successful registration
  - Auto-login after registration

#### Logout (`/logout`)
- **POST /logout** - Invalidate session and clear CSRF token

#### Password Reset (Token-based) (`/forgot`, `/reset`)
- **GET /forgot** - View forgot password form (enter email)
- **POST /forgot** - Request password reset link via email (no account enumeration)
- **GET /reset?token=TOKEN** - View reset password form (with IP throttling for token validation)
- **POST /reset** - Complete password reset with token (token validation, password update, CSRF token rotation)

#### Password Reset (Code-based) (`/verify-code`, `/new-password`, `/resend-code`)
- **GET /verify-code?email=EMAIL** - View code verification form
- **POST /verify-code** - Verify 6-digit code sent to email
- **GET /new-password** - View new password form (only reachable after code verification)
- **POST /new-password** - Complete password reset with verified code
- **POST /resend-code** - Request new 6-digit code if not received

### Customer Features
- Product browsing with filters (category, brand, price, search)
- Shopping cart
- Checkout process
- Payment via ABA Payway or card
- Order history
- Product reviews
- Account management (profile, avatar, 2FA)
- Password reset via email

### Security Features
- Authentication filter (session-based)
- CSRF protection on all POST requests
- Rate limiting on sensitive endpoints
- BCrypt password hashing
- TOTP 2FA (Google Authenticator)
- Secure cookie handling
- Security headers (X-Frame-Options, X-Content-Type-Options, etc.)
- Role-based access control (admin vs customer)
- Storefront access filter (prevents admins from shopping)

### Performance Features
- HikariCP connection pooling
- Caffeine in-memory caching (products, categories, brands, users, carts, orders)
- Single-flight cache loading (prevents cache stampede)
- Response compression (gzip)
- Static resource caching (long Cache-Control)
- Query monitoring and metrics collection
- Session user refresh (cached, throttled)

## Testing
- Unit tests with JUnit 5 and Mockito
- Test configuration in pom.xml surefire plugin
- Tests run without database by default
- System properties for test behavior: `computerstore.mail.notifications.enabled`, `computerstore.realtime.enabled`, etc.

## Important Notes

### Filter Order (web.xml)
The filter chain order is critical:
1. EncodingFilter
2. StaticResourceCacheFilter
3. CompressionFilter
4. RequestAuditContextFilter
5. SecureCookieFilter
6. SecurityHeadersFilter
7. SessionUserRefreshFilter
8. RateLimitingFilter
9. UserRateLimitingFilter
10. AuthenticationFilter
11. StorefrontAccessFilter
12. CartCountFilter
13. ReviewCountFilter
14. SupportChannelFilter
15. AdminAuthorizationFilter
16. ComingSoonGateFilter
17. CSRFProtectionFilter

### Async Support
All filters support async operations (`<async-supported>true</async-supported>`) for the SSE `/realtime` endpoint.

### Cache Invalidation
Caches are invalidated on writes:
- Product CRUD → invalidate product cache, catalog cache, category/brand counts
- Order operations → invalidate order cache, dashboard cache
- User operations → invalidate user cache
- Cart operations → invalidate cart cache

### Schema Evolution
Optional columns (image_url, highlights, box_contents, warranty_info, source_url) are detected at runtime via `SchemaUtil.hasColumn()` to support legacy schemas.

### File Uploads
Product images and user avatars uploaded to `COMPUTERSTORE_UPLOAD_DIR` (outside webapp to survive redeployments).

### Payment Integration
ABA Payway client in `AbaPaywayClient` with configuration in `PaymentConfig`.

## View Structure
JSP views in `src/main/webapp/WEB-INF/views/`:
- `admin/` - Admin pages (products, orders, users, dashboard, etc.)
- `customer/` - Customer pages (cart, checkout, account, etc.)
- `auth/` - Authentication pages (login, register, password reset)
- `errors/` - Error pages (403, 404, 500)

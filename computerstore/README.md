# ComputerStore

Production-hardened **Jakarta EE** e-commerce web application for a
computer-components store: full storefront (catalogue, cart, checkout,
in-app mail) plus an admin back-office, with security, observability and
performance work built in as first-class parts of the codebase.

> **Stack:** Java 17 · Jakarta EE 11 (Servlet 6.1) · JSP/JSTL · Apache Tomcat 11.0 ·
> MySQL 8.4 · HikariCP · Caffeine · **123 unit tests passing** · no containers,
> no CDN (vendored Bootstrap/Chart.js)

## Quick start

```bash
# 1. Database (see docs/setup.md for the full SQL — schema.sql never drops
#    an existing database, so re-runs are safe)
mysql -u root -p
#     CREATE DATABASE computer_store ...; CREATE USER 'store_user' ...; ...

# 2. Import schema + seed
mysql -u root computer_store < src/main/resources/db/schema.sql
mysql -u root computer_store < src/main/resources/db/seed/seed-data.sql

# 3. Build (123 tests)
mvn clean package

# 4. Run under Tomcat 11, context /computerstore
export DB_URL='jdbc:mysql://localhost:3306/computer_store?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8'
export DB_USERNAME='store_user'
export DB_PASSWORD='your-password'
export COMPUTERSTORE_2FA_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export CATALINA_OPTS="-Dcomputerstore.migration.autoRun=true"
cp target/computerstore.war $CATALINA_HOME/webapps/
$CATALINA_HOME/bin/startup.sh

# 5. Verify
curl -s http://localhost:8080/computerstore/health   # → {"status":"UP",...}
```

All configuration (every env var + system property) is in
[docs/environment.md](docs/environment.md).

## Main commands

| Task | Command |
|---|---|
| Build + test | `mvn clean package` |
| Tests only | `mvn test` |
| Deploy | copy `target/computerstore.war` into `$CATALINA_HOME/webapps/` |
| Health | `curl http://localhost:8080/computerstore/health` |
| Metrics (Prometheus, admin) | `GET /computerstore/metrics` |
| Performance monitor (admin) | `GET /computerstore/admin/performance` |
| Seeded dev accounts | `admin/admin123` (SUPER_ADMIN), `customer/customer123` — **change on any real deployment**; the app logs a security ERROR at startup for any account still verifying against the seeded plaintexts |

## Feature highlights

- **Storefront** — search (name + description, escaped LIKE), category/brand/price
  filters with live counts, pagination, product detail, atomic-stock cart &
  checkout (no oversell), order history.
- **Back-office** — dashboard KPIs/charts, inventory, orders with a full
  status timeline, users & roles, categories, brands, audit-history browser.
- **Security** — BCrypt, TOTP 2FA (AES-GCM encrypted secrets), role hierarchy
  with a SQL-only SUPER_ADMIN (one true root), CSRF, IP + per-user rate
  limiting, security headers (CSP `'self'`), 730-day audit archive.
- **Real-time** — SSE `/realtime` pushes stock / cart / unread-mail changes
  without polling.
- **Performance** — gzip HTTP compression (dynamic text only), single-flight
  Caffeine caches for products/users/dashboard with write-through
  invalidation, pre-warmed HikariCP pool, MySQL covering indexes, and a live
  `/admin/performance` monitor (see [docs/performance.md](docs/performance.md)).
- **Ops** — JSON `/health` with live DB-pool stats, Prometheus `/metrics`,
  cache invalidation on every stock write.

## Architecture Overview

The application follows a **layered architecture** with clear separation of concerns:

### Layer Responsibilities

1. **Core Layer** (`core/`)
   - Contains pure business logic independent of web concerns
   - Domain models (entities, DTOs) represent business concepts
   - Repository pattern for data access abstraction
   - Service interfaces define business operations
   - Custom exceptions for domain-specific error handling

2. **Web Layer** (`web/`)
   - Handles HTTP request/response processing
   - Controllers coordinate application workflows
   - Filters provide cross-cutting concerns (security, performance, monitoring)
   - No business logic - delegates to core services

3. **Infrastructure Layer** (`infrastructure/`)
   - Technical concerns external to business logic
   - Caching, monitoring, persistence, messaging
   - Can be swapped without affecting core business logic

4. **Utility Layer** (`util/`)
   - Reusable helper functions
   - Minimal, well-tested utilities
   - No dependencies on application-specific code

### Design Patterns

- **Repository Pattern**: Abstracts data access behind interfaces
- **Service Layer Pattern**: Encapsulates business logic
- **DTO Pattern**: Separates domain models from presentation models
- **Filter Chain Pattern**: Modular cross-cutting concerns
- **Dependency Injection**: Manual DI via AppContext

### Package Organization Principles

- **High Cohesion**: Related functionality grouped together
- **Low Coupling**: Layers depend on abstractions, not implementations
- **Single Responsibility**: Each package has one clear purpose
- **Stable Dependencies**: Core depends on nothing, Web depends on Core, etc.

## Development Workflow

### Setting Up Development Environment

```bash
# 1. Clone repository
git clone <repository-url>
cd computerstore

# 2. Install Java 17 and Maven
# Verify versions
java -version  # Should be 17+
mvn -version   # Should be 3.8+

# 3. Set up MySQL database
mysql -u root -p
CREATE DATABASE computer_store;
CREATE USER 'store_user'@'localhost' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON computer_store.* TO 'store_user'@'localhost';
FLUSH PRIVILEGES;

# 4. Import schema and seed data
mysql -u root computer_store < src/main/resources/db/schema.sql
mysql -u root computer_store < src/main/resources/db/seed/seed-data.sql

# 5. Build the project
mvn clean package

# 6. Run tests
mvn test
```

### Coding Standards

- **Package Structure**: Follow the established layered architecture
- **Naming Conventions**: Java standard naming (PascalCase for classes, camelCase for methods)
- **Comments**: Javadoc for public APIs, inline comments for complex logic
- **Error Handling**: Use custom exceptions from `core.exception` package
- **Logging**: Use SLF4J, appropriate log levels (ERROR, WARN, INFO, DEBUG)

### Testing Guidelines

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test component interactions
- **Test Coverage**: Maintain high coverage for core business logic
- **Test Naming**: Clear, descriptive test method names
- **Test Data**: Use seeded data for consistency

### Commit Guidelines

- **Commit Messages**: Clear, concise, following conventional commits
- **Branching**: Feature branches for new functionality
- **Code Review**: All changes should be reviewed before merging
- **Testing**: Ensure all tests pass before committing

## Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork the repository** and create a feature branch
2. **Follow the coding standards** and architecture patterns
3. **Write tests** for new functionality
4. **Update documentation** as needed
5. **Submit a pull request** with a clear description

### Issues and Bug Reports

- Use GitHub Issues for bug reports and feature requests
- Provide clear steps to reproduce issues
- Include environment details (OS, Java version, etc.)

## Performance Characteristics

- **Response Times**: < 100ms for cached operations, < 500ms for database operations
- **Throughput**: 350-530 requests/second under load
- **Caching**: Multi-layer caching (Caffeine for in-memory, database for persistence)
- **Connection Pooling**: HikariCP with pre-warmed connections
- **Compression**: Gzip compression for text-based responses

## Security Considerations

- **Authentication**: BCrypt password hashing, TOTP 2FA support
- **Authorization**: Role-based access control with SUPER_ADMIN override
- **CSRF Protection**: Token-based CSRF protection for state-changing operations
- **Rate Limiting**: IP-based and per-user rate limiting
- **Security Headers**: Content Security Policy, HSTS, X-Frame-Options
- **Audit Logging**: Comprehensive audit trail for security events

## Monitoring and Observability

- **Health Checks**: `/health` endpoint with system status
- **Metrics**: Prometheus metrics at `/metrics` endpoint
- **Logging**: Structured logging with correlation IDs
- **Performance Monitoring**: `/admin/performance` dashboard
- **Query Monitoring**: Slow query detection and logging

## Documentation

Everything you need — setup, architecture, database, API, security,
deployment, troubleshooting — lives in **[docs/](docs/README.md)**.
Highlights:

| Topic | Where |
|---|---|
| From zero to running app | [docs/setup.md](docs/setup.md) |
| Architecture & design decisions | [docs/architecture.md](docs/architecture.md) |
| Database schema, ER diagram, migrations | [docs/database.md](docs/database.md) |
| Every endpoint (HTML, JSON, SSE) | [docs/api.md](docs/api.md) |
| Auth, roles, 2FA, password reset | [docs/authentication.md](docs/authentication.md) |
| Env vars & system properties | [docs/environment.md](docs/environment.md) |
| Performance: compression, caches, pool, indexes | [docs/performance.md](docs/performance.md) |
| Production deployment + tuning | [docs/deployment.md](docs/deployment.md) |
| Real web store go-live (Amazon-style) | [docs/go-live.md](docs/go-live.md) |
| Security model & known gaps | [docs/security.md](docs/security.md) |
| Troubleshooting | [docs/troubleshooting.md](docs/troubleshooting.md) |
| Changelog | [docs/changelog.md](docs/changelog.md) |

## Repository layout

```text
computerstore/
├── pom.xml                    WAR build (Java 17, Maven, test coverage)
├── README.md                  this file
├── docs/                      comprehensive developer documentation
│   ├── README.md             documentation index
│   ├── setup.md              installation & initial setup
│   ├── architecture.md       system architecture & design patterns
│   ├── database.md           schema, ER diagram, migrations
│   ├── api.md                complete API reference
│   ├── authentication.md    auth, roles, 2FA, password reset
│   ├── environment.md        all configuration options
│   ├── performance.md       caching, compression, optimization
│   ├── deployment.md        production deployment guide
│   ├── go-live.md           real-world launch checklist
│   ├── security.md          security model & best practices
│   ├── troubleshooting.md   common issues & solutions
│   └── changelog.md         version history
├── src/main/java/com/example/computer_store/
│   ├── core/                # Core business logic layer
│   │   ├── config/          # Application configuration
│   │   │   ├── AppConfig.java
│   │   │   ├── AppContext.java
│   │   │   └── AppContextListener.java
│   │   ├── domain/          # Domain models
│   │   │   ├── entity/      # JPA entities (16 files)
│   │   │   │   ├── User.java
│   │   │   │   ├── Product.java
│   │   │   │   ├── Order.java
│   │   │   │   ├── Category.java
│   │   │   │   ├── Brand.java
│   │   │   │   ├── Review.java
│   │   │   │   ├── CartItem.java
│   │   │   │   ├── OrderItem.java
│   │   │   │   ├── InventoryLog.java
│   │   │   │   ├── PasswordResetToken.java
│   │   │   │   ├── AuditLog.java
│   │   │   │   ├── AuditAlert.java
│   │   │   │   ├── MailMessage.java
│   │   │   │   ├── OrderStatusEvent.java
│   │   │   │   ├── ProductSpec.java
│   │   │   │   └── RatingSummary.java
│   │   │   └── dto/         # Data transfer objects (5 files)
│   │   │       ├── ProductCardVM.java
│   │   │       ├── ProductDetailVM.java
│   │   │       ├── ProductFormVM.java
│   │   │       ├── ActiveFilterVM.java
│   │   │       └── ProductViewMapper.java
│   │   ├── repository/      # Data access layer (11 files)
│   │   │   ├── UserRepository.java
│   │   │   ├── ProductRepository.java
│   │   │   ├── OrderRepository.java
│   │   │   ├── CartRepository.java
│   │   │   ├── CategoryRepository.java
│   │   │   ├── BrandRepository.java
│   │   │   ├── ReviewRepository.java
│   │   │   ├── InventoryRepository.java
│   │   │   ├── MailRepository.java
│   │   │   ├── PasswordResetTokenRepository.java
│   │   │   └── AuditLogRepository.java
│   │   ├── service/         # Business logic interfaces (20 files)
│   │   │   ├── UserService.java
│   │   │   ├── ProductService.java
│   │   │   ├── OrderService.java
│   │   │   ├── CartService.java
│   │   │   ├── AuthService.java
│   │   │   ├── ReviewService.java
│   │   │   ├── CategoryService.java
│   │   │   ├── BrandService.java
│   │   │   ├── InventoryService.java
│   │   │   ├── MailService.java
│   │   │   ├── NotificationService.java
│   │   │   ├── PasswordResetService.java
│   │   │   ├── DashboardService.java
│   │   │   ├── ReportService.java
│   │   │   ├── AuditLogService.java
│   │   │   └── impl/        # Service implementations
│   │   │       ├── AuthServiceImpl.java
│   │   │       ├── CartServiceImpl.java
│   │   │       ├── OrderServiceImpl.java
│   │   │       └── ProductServiceImpl.java
│   │   └── exception/      # Custom exceptions (3 files)
│   │       ├── NotFoundException.java
│   │       ├── ValidationException.java
│   │       └── InsufficientStockException.java
│   ├── web/                # Web layer
│   │   ├── controller/      # HTTP controllers (36 files)
│   │   │   ├── admin/       # Admin controllers
│   │   │   │   ├── DashboardServlet.java
│   │   │   │   ├── AdminProductsServlet.java
│   │   │   │   ├── AdminCategoriesServlet.java
│   │   │   │   ├── AdminBrandsServlet.java
│   │   │   │   ├── AdminOrdersServlet.java
│   │   │   │   ├── AdminUsersServlet.java
│   │   │   │   ├── AdminReviewsServlet.java
│   │   │   │   ├── AdminInventoryServlet.java
│   │   │   │   ├── AdminMailServlet.java
│   │   │   │   ├── AdminReportsServlet.java
│   │   │   │   ├── AdminHistoryServlet.java
│   │   │   │   └── PerformanceMonitoringServlet.java
│   │   │   ├── auth/        # Authentication controllers
│   │   │   │   ├── LoginServlet.java
│   │   │   │   ├── LogoutServlet.java
│   │   │   │   ├── RegisterServlet.java
│   │   │   │   ├── ForgotPasswordServlet.java
│   │   │   │   └── ResetPasswordServlet.java
│   │   │   ├── customer/    # Customer controllers
│   │   │   │   ├── ProductServlet.java
│   │   │   │   ├── CartServlet.java
│   │   │   │   ├── CheckoutServlet.java
│   │   │   │   ├── AccountServlet.java
│   │   │   │   ├── ProfileServlet.java
│   │   │   │   ├── CustomerOrdersServlet.java
│   │   │   │   ├── ReviewServlet.java
│   │   │   │   ├── MailServlet.java
│   │   │   │   ├── MailJsonServlet.java
│   │   │   │   ├── CartCountServlet.java
│   │   │   │   ├── AvatarServlet.java
│   │   │   │   ├── AvatarImageServlet.java
│   │   │   │   ├── ProductImageServlet.java
│   │   │   │   ├── SecuritySettingsServlet.java
│   │   │   │   └── TwoFactorSetupServlet.java
│   │   │   ├── base/        # Base servlet
│   │   │   │   └── BaseServlet.java
│   │   │   ├── error/       # Error handling
│   │   │   │   └── ErrorServlet.java
│   │   │   └── monitoring/  # Monitoring endpoints
│   │   │       └── HealthCheckServlet.java
│   │   └── filter/          # Servlet filters (15 files)
│   │       ├── security/    # Security filters
│   │       │   ├── AuthenticationFilter.java
│   │       │   ├── AdminAuthorizationFilter.java
│   │       │   ├── CSRFProtectionFilter.java
│   │       │   ├── RateLimitingFilter.java
│   │       │   ├── UserRateLimitingFilter.java
│   │       │   ├── SecurityHeadersFilter.java
│   │       │   ├── SecureCookieFilter.java
│   │       │   └── SessionUserRefreshFilter.java
│   │       ├── performance/ # Performance filters
│   │       │   ├── CompressionFilter.java
│   │       │   └── StaticResourceCacheFilter.java
│   │       ├── monitoring/  # Monitoring filters
│   │       │   └── RequestAuditContextFilter.java
│   │       └── web/         # Web filters
│   │           ├── EncodingFilter.java
│   │           ├── CartCountFilter.java
│   │           ├── MailCountFilter.java
│   │           └── ReviewCountFilter.java
│   ├── infrastructure/      # Infrastructure concerns
│   │   ├── cache/           # Caching infrastructure
│   │   │   └── CacheManager.java
│   │   ├── monitoring/      # Monitoring/observability
│   │   │   ├── MetricsCollector.java
│   │   │   ├── MetricsServlet.java
│   │   │   └── QueryMonitor.java
│   │   ├── optimization/     # Query batching
│   │   │   └── QueryBatchOptimizer.java
│   │   ├── realtime/        # Real-time updates
│   │   │   ├── EventHub.java
│   │   │   └── RealtimeStreamServlet.java
│   │   ├── security/        # Security infrastructure
│   │   │   └── TwoFactorAuthService.java
│   │   ├── persistence/     # Database utilities
│   │   │   ├── DBConnection.java
│   │   │   ├── ConnectionProvider.java
│   │   │   ├── SchemaUtil.java
│   │   │   └── DatabaseMigrationRunner.java
│   │   └── messaging/       # Email/messaging
│   │       └── EmailUtil.java
│   └── util/                # Minimal utilities
│       ├── cache/           # Cache utilities
│       │   └── CountCache.java
│       ├── file/            # File utilities
│       │   ├── FileUploadUtil.java
│       │   └── UploadConfig.java
│       ├── security/        # Security utilities
│       │   ├── PasswordUtil.java
│       │   ├── CSRFUtil.java
│       │   └── DefaultCredentialsChecker.java
│       ├── time/            # Time utilities
│       │   └── TimeBoundaries.java
│       ├── validation/      # Validation utilities
│       │   └── ValidationUtil.java
│       └── web/             # Web utilities
│           ├── AuditLogger.java
│           ├── ErrorHandler.java
│           ├── Flash.java
│           └── RequestUtil.java
├── src/main/resources/       # Configuration & resources
│   ├── db/                  # Database files
│   │   ├── schema.sql       # Database schema
│   │   ├── seed/            # Seed data
│   │   │   └── seed-data.sql
│   │   └── migrations/      # Database migrations
│   ├── logback.xml          # Logging configuration
│   └── *.properties         # Application properties (git-ignored)
├── src/main/webapp/          # Web application resources
│   ├── WEB-INF/             # Web configuration
│   │   └── web.xml          # Servlet configuration
│   ├── assets/              # Static assets
│   └── views/               # JSP views
│       ├── admin/           # Admin pages
│       ├── customer/        # Customer pages
│       ├── auth/            # Authentication pages
│       └── error/           # Error pages
└── src/test/java/           # Test sources
    └── com/example/computer_store/
        ├── core/            # Core layer tests
        ├── infrastructure/  # Infrastructure tests
        ├── web/             # Web layer tests
        └── util/            # Utility tests
```

## Project status

- HEAD: **release 1.2.1** (`997ca11`, fix & hardening pass, released); a
  **1.3.0** body of work (performance pass: HTTP compression, repository/dashboard
  caching, pool pre-warm, covering indexes, batch utils, `/admin/performance`)
  is in the working tree — described in [docs/changelog.md](docs/changelog.md).
- **123/123 tests passing** (hermetic; no DB or network); load-tested at
  350–530 req/s catalogue (100 clients, shared host), 0 real errors.
- **Recent restructuring**: Project has been reorganized with clean layered architecture:
  - `core/` - Core business logic (domain models, repositories, services, exceptions)
  - `web/` - Web layer (controllers, filters)
  - `infrastructure/` - Infrastructure concerns (caching, monitoring, persistence, messaging)
  - `util/` - Minimal utilities (validation, security, file operations)
- Deployment target: Tomcat on-host; production VM on Oracle Cloud **Always
  Free** with Caddy TLS (see [docs/deployment.md](docs/deployment.md)).
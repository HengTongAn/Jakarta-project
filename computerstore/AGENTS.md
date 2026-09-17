# Computer Store Project - Deep Analysis

## Project Overview
This is a full-featured **Jakarta EE 11** e-commerce application for a computer store, built with traditional Java servlets and JSP. It implements a complete shopping system with customer-facing features and comprehensive admin management.

## Technology Stack

### Core Technologies
- **Jakarta EE 11** (Servlet 6.1, JSP 4.0)
- **Java 17** (Maven compiler release)
- **MySQL 8.x** database
- **Maven 3.9** for build management
- **Tomcat 11** (servlet container)

### Key Dependencies
- `jakarta.servlet-api` 6.1.0
- `jakarta.servlet.jsp.jstl` 3.0.2 (JSTL for JSP)
- `mysql-connector-j` 8.4.0 (JDBC driver)
- `jbcrypt` 0.4 (password hashing)
- `jakarta.mail` 2.0.1 (email notifications)

## Architecture

### Layered Architecture
```
┌─────────────────────────────────────────┐
│  Presentation Layer (JSP + Servlets)     │
├─────────────────────────────────────────┤
│  Controller Layer (Servlets)            │
├─────────────────────────────────────────┤
│  Service Layer (Business Logic)         │
├─────────────────────────────────────────┤
│  DAO Layer (Data Access)                │
├─────────────────────────────────────────┤
│  Database (MySQL)                       │
└─────────────────────────────────────────┘
```

### Package Structure
- `controller/` - Servlets organized by role (admin, customer, auth)
- `service/` - Business logic layer
- `dao/` - Data access objects
- `model/` - Domain entities
- `util/` - Utility classes (DB connection, validation, security)
- `filter/` - Servlet filters (security, encoding, counting)
- `exception/` - Custom exceptions

## Database Schema

### Core Tables
- **users** - Customers and admins with role-based access
- **categories** - Product categorization
- **brands** - Product brands
- **products** - Product catalog with stock management
- **orders** - Customer orders with status tracking
- **order_items** - Order line items with historical pricing
- **cart_items** - Shopping cart persistence
- **inventory_logs** - Stock change audit trail
- **mail_messages** - In-app messaging system

### Key Features
- Foreign key constraints for referential integrity
- Status enums for products (IN_STOCK, LOW_STOCK, OUT_OF_STOCK, DISCONTINUED)
- Order status workflow (PENDING → PROCESSING → COMPLETED/CANCELLED)
- Unique constraints on critical fields (username, email, SKU)
- Automatic timestamp management

## Security Implementation

### Authentication & Authorization
- **Session-based authentication** with user roles (ADMIN/CUSTOMER)
- **AuthenticationFilter** - Protects admin, cart, checkout, account pages
- **AdminAuthorizationFilter** - Role-based access control for admin routes
- **BCrypt password hashing** (10 rounds) for secure credential storage
- **Session configuration** - 30-minute timeout, HTTP-only cookies, SameSite=Strict

### CSRF Protection
- **CSRFProtectionFilter** - Validates tokens on all POST requests
- **CSRFUtil** - Token generation and validation using UUIDs
- Excludes login/register/logout endpoints
- Supports both parameter and header-based token submission

### Additional Security Features
- **EncodingFilter** - UTF-8 encoding for all requests
- **SecurityHeadersFilter** - Security headers injection
- **RateLimitingFilter** - Request rate limiting
- **Input validation** - Comprehensive validation utilities
- **SQL injection prevention** - Prepared statements throughout
- **Audit logging** - Authentication events, admin actions, security events

## Core Business Logic

### Product Management
- **ProductService** - CRUD operations with validation
- **Stock management** - Automatic status computation based on quantity
- **Image support** - Optional product images with schema detection
- **Soft delete** - Products with order history marked as DISCONTINUED
- **Search & filtering** - Multi-criteria search with category/brand/price filters

### Order Processing
- **Transaction management** - ACID-compliant checkout process
- **Stock reservation** - Atomic stock reduction with rollback on failure
- **Inventory logging** - Automatic audit trail for stock changes
- **Order cancellation** - Stock restoration with audit logging
- **Historical pricing** - Order items preserve purchase prices

### Shopping Cart
- **Persistent cart** - Database-backed cart per user
- **Stock validation** - Prevents over-ordering available stock
- **Quantity management** - Add, update, remove operations
- **Real-time totals** - Dynamic cart total calculation

### User Management
- **Registration** - Username/email uniqueness, password strength validation
- **Profile management** - User profile updates
- **Role-based access** - ADMIN vs CUSTOMER permissions
- **Password security** - BCrypt hashing, strength requirements (8+ chars, mixed case, digits, special chars)

## User Interface

### Customer Features
- **Product browsing** - Search, filter by category/brand/price
- **Product details** - Individual product pages with stock status
- **Shopping cart** - Add/remove items, quantity adjustment
- **Checkout** - Cart-to-order conversion with stock validation
- **Order history** - View past orders with details
- **Account management** - Profile updates, password changes
- **Mail system** - Gmail-style messaging with admin

### Admin Features
- **Dashboard** - Real-time statistics (products, customers, orders, revenue, stock alerts)
- **Product management** - Create, edit, delete products with image upload
- **Category/brand management** - Organize product catalog
- **Order management** - View, update order status, handle cancellations
- **Inventory management** - Stock adjustments with audit trail
- **Customer management** - View and manage customer accounts
- **Mail system** - Customer communication inbox
- **Reports** - Sales and inventory analytics

### Frontend Technology
- **JSP with JSTL** - Server-side rendering
- **Bootstrap 5.3** - Responsive UI framework
- **Custom CSS** - Modern styling with CSS variables
- **JavaScript** - Interactive features (mail live updates)
- **Flash messages** - User feedback system

## Key Design Patterns

### DAO Pattern
- Each entity has dedicated DAO (ProductDAO, UserDAO, OrderDAO, etc.)
- Centralized database access logic
- Connection management via DBConnection utility

### Service Layer Pattern
- Business logic separated from controllers
- Transaction management in service layer
- Validation and exception handling

### Filter Chain Pattern
- Multiple filters for cross-cutting concerns
- Ordered execution (encoding → auth → admin auth → CSRF)
- Clean separation of security logic

### Front Controller Pattern
- Servlets act as controllers for specific URL patterns
- Request routing based on HTTP methods and parameters
- Centralized error handling

## Exception Handling

### Custom Exceptions
- **ValidationException** - Input validation failures
- **NotFoundException** - Resource not found
- **InsufficientStockException** - Stock availability issues

### Error Handling
- **ErrorHandler** utility for consistent error responses
- Generic user messages with detailed logging
- Custom error pages (403, 404, 500)

## Configuration Management

### Database Configuration
- **db.properties** - Database connection settings
- **Environment variable support** - DB_URL, DB_USERNAME, DB_PASSWORD
- **Schema detection** - Automatic image_url column detection

### Mail Configuration
- **mail.properties** - SMTP settings for notifications
- Gmail integration with app password support
- Best-effort email delivery

## Development & Build

### Build Commands
```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package WAR
mvn clean package

# Deploy to Tomcat
# Copy target/computerstore.war to Tomcat webapps
```

### Database Setup
```bash
# Execute schema
mysql -u root -p < src/main/resources/db/schema.sql

# Load seed data
mysql -u root -p computer_store < src/main/resources/db/seed-data.sql
```

## Code Quality Features

### Validation
- Comprehensive input validation (length, format, business rules)
- Password strength requirements with common password detection
- Email format validation with regex patterns
- Username format validation (alphanumeric + underscore)

### Logging
- **AuditLogger** - Security event logging
- **ErrorHandler** - Error logging with context
- Separate audit logger for security-sensitive operations

### Utilities
- **ValidationUtil** - Input validation helpers
- **PasswordUtil** - BCrypt password hashing
- **CSRFUtil** - CSRF token management
- **Flash** - Flash message system for user feedback
- **FileUploadUtil** - Image upload handling

## Deployment Considerations

### Environment Variables
- `DB_URL` - Database connection URL
- `DB_USERNAME` - Database user
- `DB_PASSWORD` - Database password

### Session Configuration
- 30-minute session timeout
- HTTP-only cookies
- SameSite=Strict for CSRF protection
- Cookie-based session tracking

### File Upload
- Max file size: 5MB
- Max request size: 10MB
- File size threshold: 1MB

## Testing
- JUnit 5.10.2 for unit testing
- Maven Surefire plugin for test execution
- Test scope dependencies

## Notable Implementation Details

### Schema Evolution Support
- ProductDAO detects image_url column presence at runtime
- Backward compatible with older database schemas
- Dynamic SQL generation based on available columns

### Transaction Safety
- Order processing uses explicit transaction management
- Rollback on any failure during checkout
- Stock changes are atomic to prevent race conditions

### Inventory Audit Trail
- All stock changes logged with old/new values
- Automatic logging for order creation/cancellation
- User attribution for manual adjustments

### Customer-Admin Communication
- Built-in mail system for customer support
- Gmail-style inbox/sent/compose functionality
- Optional SMTP email notifications

## Build & Run Instructions

1. **Setup MySQL database**
   ```bash
   mysql -u root -p < src/main/resources/db/schema.sql
   mysql -u root -p computer_store < src/main/resources/db/seed-data.sql
   ```

2. **Configure database connection**
   - Edit `src/main/resources/db.properties` or set environment variables

3. **Build the project**
   ```bash
   mvn clean package
   ```

4. **Deploy to Tomcat**
   - Copy `target/computerstore.war` to Tomcat webapps directory

5. **Access the application**
   - Customer: http://localhost:8080/computerstore/products
   - Admin: http://localhost:8080/computerstore/admin
   - Default credentials: admin/admin123, customer/customer123

## Project Strengths

1. **Clean architecture** with clear separation of concerns
2. **Comprehensive security** (CSRF, authentication, authorization, input validation)
3. **Transaction safety** for critical operations
4. **Audit logging** for security-sensitive operations
5. **Responsive UI** with modern design
6. **Schema evolution support** for backward compatibility
7. **Environment-based configuration** for flexible deployment

## Default Credentials

- **Admin**: username `admin`, password `admin123`
- **Customer**: username `customer`, password `customer123`

## File Structure Summary

```
computerstore/
├── src/main/
│   ├── java/com/example/computer_store/
│   │   ├── controller/ (Servlets)
│   │   ├── service/ (Business logic)
│   │   ├── dao/ (Data access)
│   │   ├── model/ (Domain entities)
│   │   ├── util/ (Utilities)
│   │   ├── filter/ (Servlet filters)
│   │   └── exception/ (Custom exceptions)
│   ├── resources/
│   │   ├── db/ (SQL scripts)
│   │   ├── db.properties (Database config)
│   │   └── mail.properties (Mail config)
│   └── webapp/
│       ├── WEB-INF/
│       │   ├── views/ (JSP pages)
│       │   └── web.xml (Servlet config)
│       ├── assets/ (CSS, JS)
│       └── index.jsp (Welcome page)
└── pom.xml (Maven configuration)
```
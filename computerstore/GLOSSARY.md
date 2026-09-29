# Computer Store Project - Learning Glossary

## 📚 **Project Overview**
This is a Java EE web application for an e-commerce computer store. It allows customers to browse products, add items to cart, checkout, and manage their accounts. Administrators can manage inventory, orders, users, and payments.

---

## 🛠️ **Technologies & Frameworks**

### **Backend Technologies**
- **Java EE (Jakarta EE)**: Enterprise Java platform for web applications
- **Tomcat**: Web server that runs the Java application
- **JDBC (Java Database Connectivity)**: API for connecting to databases
- **JSP (JavaServer Pages)**: Server-side templating technology for generating HTML
- **Servlets**: Java classes that handle HTTP requests and responses
- **Maven**: Build automation tool for managing dependencies and building the project

### **Frontend Technologies**
- **Bootstrap 5**: CSS framework for responsive design
- **Bootstrap Icons**: Icon library
- **Chart.js**: JavaScript library for data visualization
- **Vanilla JavaScript**: Plain JavaScript (no frameworks like React/Vue)

### **Database**
- **MySQL 8.4**: Relational database management system
- **HikariCP**: High-performance JDBC connection pooling

---

## 🏗️ **Architecture Patterns**

### **Layered Architecture**
The project follows a 4-layer architecture:

1. **Web Layer** (`web/controller`, `web/filter`)
   - Handles HTTP requests/responses
   - Parses user input
   - Renders views or redirects

2. **Service Layer** (`core/service`)
   - Contains business logic
   - Manages transactions
   - Enforces business rules

3. **Repository Layer** (`core/repository`)
   - Handles database operations
   - Maps SQL results to Java objects
   - No business logic, just data access

4. **Domain Layer** (`core/domain`)
   - Contains entities (data models)
   - Enums for constants
   - Plain data with behavior

### **Design Patterns Used**
- **Repository Pattern**: Separates data access logic from business logic
- **Factory Pattern**: Creates objects (e.g., `AppContext` creates services)
- **Filter Pattern**: Processes requests before they reach servlets
- **Singleton Pattern**: Single instance of `AppContext` for the application
- **DTO Pattern**: Data Transfer Objects for moving data between layers

---

## 📁 **Key Directory Structure**

```
src/
├── main/
│   ├── java/com/hengtongan/computerstore/
│   │   ├── core/                    # Business logic layer
│   │   │   ├── config/             # Application configuration
│   │   │   ├── domain/             # Entities and enums
│   │   │   ├── repository/         # Database access
│   │   │   └── service/            # Business rules
│   │   ├── infrastructure/         # External system integrations
│   │   │   ├── cache/              # Caching implementation
│   │   │   ├── messaging/          # Email sending
│   │   │   ├── persistence/        # Database connection
│   │   │   ├── realtime/           # Server-sent events
│   │   │   └── security/           # Security utilities
│   │   ├── util/                   # Utility classes
│   │   │   ├── json/               # JSON handling
│   │   │   ├── security/           # Security helpers
│   │   │   ├── validation/         # Input validation
│   │   │   └── web/                # Web utilities
│   │   └── web/                    # Web layer
│   │       ├── config/             # Web configuration
│   │       ├── controller/         # Request handlers
│   │       │   ├── admin/          # Admin-specific controllers
│   │       │   ├── auth/           # Authentication controllers
│   │       │   ├── customer/       # Customer-facing controllers
│   │       │   ├── error/          # Error handling
│   │       │   └── monitoring/     # Health checks
│   │       ├── filter/             # Request filters
│   │       │   ├── performance/    # Performance filters
│   │       │   └── security/       # Security filters
│   │       └── view/               # View-related utilities
│   ├── resources/
│   │   ├── db/                     # Database files
│   │   │   ├── migrations/         # Database schema changes
│   │   │   └── seed/              # Sample data
│   │   └── config/                 # Configuration files
│   └── webapp/
│       ├── WEB-INF/
│       │   ├── views/              # JSP pages
│       │   │   ├── layouts/       # Reusable page components
│       │   │   ├── admin/          # Admin pages
│       │   │   ├── auth/           # Authentication pages
│       │   │   └── customer/       # Customer pages
│       │   └── web.xml            # Web application configuration
│       ├── assets/                 # Static files
│       │   ├── css/                # Stylesheets
│       │   ├── js/                 # JavaScript files
│       │   └── images/            # Images and icons
└── test/                           # Test files
```

---

## 🔑 **Key Classes & Their Purposes**

### **Configuration**
- **`AppContext`**: Central configuration class that creates and manages service instances (singleton pattern)
- **`AppConfig`**: Loads configuration from environment variables, system properties, and config files
- **`DBConnection`**: Manages database connection pool using HikariCP

### **Domain Entities**
- **`User`**: Represents application users (customers, admins)
- **`Product`**: Represents products in the store
- **`Category`**: Product categories (Laptops, Processors, etc.)
- **`Order`**: Customer orders
- **`CartItem`**: Items in shopping cart
- **`Review`**: Product reviews
- **`AuditLog`**: Security and action logging

### **Services**
- **`UserService`**: User management (authentication, profile)
- **`ProductService`**: Product catalog management
- **`OrderService`**: Order processing and management
- **`CartService`**: Shopping cart operations
- **`ReviewService`**: Product review management
- **`PaymentService`**: Payment processing
- **`NotificationService`**: Email notifications

### **Controllers (Servlets)**
- **`ProductServlet`**: Handles product browsing and search
- **`CartServlet`**: Shopping cart operations
- **`CheckoutServlet`**: Checkout process
- **`LoginServlet`/`RegisterServlet`**: Authentication
- **`AdminProductsServlet`**: Admin product management
- **`AdminOrdersServlet`**: Admin order management

### **Filters**
- **`AuthenticationFilter`**: Ensures user is logged in for protected routes
- **`AdminAuthorizationFilter`**: Ensures user has admin privileges
- **`CSRFProtectionFilter`**: Protects against CSRF attacks
- **`RateLimitingFilter`**: Limits request rate to prevent abuse
- **`CompressionFilter`**: Compresses responses for better performance

---

## 🗄️ **Database Terms**

### **Schema Components**
- **`users`**: Stores user accounts and authentication data
- **`products`**: Product catalog
- **`categories`**: Product categories
- **`brands`**: Product brands
- **`orders`**: Customer orders
- **`order_items`**: Individual items in orders
- **`cart_items`**: Shopping cart contents
- **`reviews`**: Product reviews
- **`audit_log`**: Security and action logging
- **`app_settings`**: Application configuration stored in database
- **`schema_migrations`**: Tracks which database migrations have been applied

### **Database Concepts**
- **Migration**: Scripts that modify database schema over time
- **Seed Data**: Initial sample data for development/testing
- **Connection Pool**: Reusable database connections for performance
- **Transaction**: Group of database operations that succeed or fail together
- **Prepared Statement**: SQL template that prevents SQL injection

---

## 🔒 **Security Concepts**

### **Authentication & Authorization**
- **Authentication**: Verifying who a user is (login)
- **Authorization**: Verifying what a user can do (permissions)
- **Session**: Server-side storage of user login state
- **CSRF (Cross-Site Request Forgery)**: Attack where malicious site makes requests on user's behalf
- **CSRF Token**: Unique token that prevents CSRF attacks
- **BCrypt**: Password hashing algorithm for secure password storage

### **Security Measures**
- **Rate Limiting**: Limiting how many requests a user can make
- **Input Validation**: Checking user input for malicious content
- **SQL Injection Prevention**: Using prepared statements instead of string concatenation
- **XSS Prevention**: Escaping user-generated content
- **Audit Logging**: Recording security-relevant events

---

## 🌐 **Web Development Terms**

### **HTTP Concepts**
- **GET**: Request to retrieve data
- **POST**: Request to submit data
- **PUT/PATCH**: Request to update data
- **DELETE**: Request to remove data
- **Request**: Data sent from client to server
- **Response**: Data sent from server to client
- **Status Codes**: 200 (success), 404 (not found), 500 (server error)

### **Frontend Concepts**
- **DOM (Document Object Model)**: Programming interface for HTML documents
- **Event Listener**: JavaScript function that responds to user actions
- **AJAX**: Making HTTP requests without reloading the page
- **Responsive Design**: Design that works on different screen sizes
- **Progressive Enhancement**: Building basic functionality first, then enhancing

### **JavaScript Concepts**
- **Vanilla JS**: Plain JavaScript without frameworks
- **Event Loop**: JavaScript's mechanism for handling async operations
- **Callback**: Function passed as argument to another function
- **Promise**: Object representing eventual completion of async operation
- **LocalStorage**: Browser storage for client-side data

---

## 🧪 **Testing Terms**

### **Test Types**
- **Unit Test**: Tests individual methods/classes in isolation
- **Integration Test**: Tests how multiple components work together
- **End-to-End Test**: Tests entire application flow
- **Coverage**: Percentage of code executed by tests

### **Testing Concepts**
- **Mock**: Fake object that simulates real dependencies
- **Assertion**: Statement that checks if a condition is true
- **Test Fixture**: Fixed state for running tests
- **Test Suite**: Collection of related tests

---

## 🚀 **Development Practices**

### **Build & Deployment**
- **Build**: Compiling and packaging the application
- **WAR File**: Web Application Archive - deployable Java web application
- **Dependency**: External library/project that your project needs
- **Repository**: Storage for code (Git)
- **Commit**: Saving changes to repository

### **Code Quality**
- **Refactoring**: Improving code structure without changing behavior
- **Code Review**: Process of examining code by other developers
- **Linting**: Automated checking of code style and potential errors
- **Technical Debt**: Cost of choosing easy solution now vs. proper solution later

---

## 📊 **Performance Concepts**

### **Caching**
- **Cache**: Storing frequently used data for faster access
- **Cache Hit**: Data found in cache
- **Cache Miss**: Data not found in cache (must fetch from source)
- **TTL (Time To Live)**: How long data stays in cache
- **Cache Invalidation**: Removing stale data from cache

### **Optimization**
- **Lazy Loading**: Loading data only when needed
- **Eager Loading**: Loading data immediately
- **Connection Pooling**: Reusing database connections
- **Compression**: Reducing response size for faster transfer
- **Minification**: Removing unnecessary characters from CSS/JS

---

## 🎯 **Key Learning Points for Tonight**

### **Start Here (Beginner)**
1. Understand the **directory structure** - where files are located
2. Learn **HTTP basics** - GET vs POST, request/response cycle
3. Study **JSP basics** - how Java code generates HTML
4. Examine **simple controllers** - how requests are handled

### **Intermediate Level**
1. Study **service layer** - business logic separation
2. Learn **repository pattern** - database access abstraction
3. Understand **filters** - how requests are processed
4. Examine **security** - authentication and authorization

### **Advanced Level**
1. Study **architecture patterns** - layered design
2. Learn **transaction management** - database consistency
3. Understand **caching strategy** - performance optimization
4. Examine **testing approach** - ensuring code quality

---

## 💡 **Quick Reference for Common Tasks**

### **Adding a New Feature**
1. Create/update **entity** in `core/domain`
2. Add **repository** methods in `core/repository`
3. Implement **service** logic in `core/service`
4. Create **controller** in `web/controller`
5. Build **view** (JSP) in `WEB-INF/views`
6. Add **routes** in `web.xml` or use `@WebServlet`

### **Debugging Issues**
1. Check **server logs** for error messages
2. Use **browser developer tools** for frontend issues
3. Add **logging statements** in code
4. Test with **database queries** directly
5. Check **filter chain** for request processing

### **Database Changes**
1. Create **migration file** in `db/migrations`
2. Add migration to `DatabaseMigrationRunner.java`
3. Update **seed data** if needed
4. Run migration with `-Dcomputerstore.migration.autoRun=true`

---

## 📖 **Recommended Learning Order**

1. **Hour 1**: Project structure and HTTP basics
2. **Hour 2**: JSP and servlet fundamentals
3. **Hour 3**: Service layer and business logic
4. **Hour 4**: Database operations and repositories
5. **Hour 5**: Security and authentication
6. **Hour 6**: Frontend JavaScript and interactions
7. **Hour 7**: Testing and quality assurance
8. **Hour 8**: Performance and optimization

---

**Good luck with your learning! This glossary should help you understand the key concepts as you explore the codebase.**
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Browse Products - TechStore"/>
<%@ include file="../common/header.jspf" %>

<section class="hero">
    <span class="hero-orb hero-orb-1"></span>
    <span class="hero-orb hero-orb-2"></span>
    <div class="container">
        <h1 class="display-4">Build your <span class="hero-grad-text">dream PC</span> today</h1>
        <p class="lead mb-4">
            Hand-picked computer parts and ready-made systems for work, study and gaming —
            from a single store, delivered to your door.
        </p>
        <form method="get" action="${pageContext.request.contextPath}/products" class="row g-2">
            <div class="col-md-8">
                <input type="text" name="search" class="form-control"
                       placeholder="Search products, brand or SKU..."
                       value="<c:out value='${param.search}'/>">
            </div>
            <div class="col-md-4 d-grid">
                <button type="submit" class="btn btn-hero btn-lg">Search</button>
            </div>
        </form>
        <div class="hero-chips">
            <span class="hero-chips-label">Popular:</span>
            <c:forEach var="cat" items="${categories}" varStatus="st">
                <c:if test="${st.index < 6}">
                    <a class="hero-chip" href="${pageContext.request.contextPath}/products?category=${cat.categoryId}">
                        <c:out value="${cat.name}"/>
                    </a>
                </c:if>
            </c:forEach>
        </div>
        <div class="hero-stats">
            <div class="hero-stat">
                <strong>${products.size()}</strong><span>Products in store</span>
            </div>
            <div class="hero-stat">
                <strong>${categories.size()}</strong><span>Categories</span>
            </div>
            <div class="hero-stat">
                <strong>${brands.size()}</strong><span>Top brands</span>
            </div>
        </div>
    </div>
</section>

<div class="container py-4">
    <div class="row">
        <div class="col-lg-3">
            <div class="card sidebar-filter mb-4">
                <div class="card-body">
                    <h6 class="filter-title">Filters</h6>
                    <form method="get" action="${pageContext.request.contextPath}/products">
                        <input type="hidden" name="search" value="<c:out value='${param.search}'/>">
                        <div class="filter-group">
                            <label class="form-label">Category</label>
                            <select name="category" class="form-select">
                                <option value="">All categories</option>
                                <c:forEach var="cat" items="${categories}">
                                    <option value="${cat.categoryId}" <c:if test="${selectedCategoryId == cat.categoryId}">selected</c:if>>
                                        <c:out value="${cat.name}"/>
                                    </option>
                                </c:forEach>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label class="form-label">Brand</label>
                            <select name="brand" class="form-select">
                                <option value="">All brands</option>
                                <c:forEach var="brand" items="${brands}">
                                    <option value="${brand.brandId}" <c:if test="${selectedBrandId == brand.brandId}">selected</c:if>>
                                        <c:out value="${brand.name}"/>
                                    </option>
                                </c:forEach>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label class="form-label">Price range</label>
                            <div class="row g-2">
                                <div class="col-6">
                                    <input type="number" step="0.01" min="0" name="minPrice" class="form-control"
                                           placeholder="Min" value="<c:out value='${param.minPrice}'/>">
                                </div>
                                <div class="col-6">
                                    <input type="number" step="0.01" min="0" name="maxPrice" class="form-control"
                                           placeholder="Max" value="<c:out value='${param.maxPrice}'/>">
                                </div>
                            </div>
                        </div>
                        <button type="submit" class="btn btn-brand w-100 mt-3">Apply filters</button>
                        <a href="${pageContext.request.contextPath}/products" class="btn btn-outline-secondary w-100 mt-2">Clear</a>
                    </form>
                </div>
            </div>
        </div>

        <div class="col-lg-9">
            <div class="d-flex justify-content-between align-items-center mb-4">
                <h5 class="mb-0 fw-bold">${products.size()} product${products.size() == 1 ? '' : 's'}</h5>
                <c:if test="${not empty param.search}">
                    <span class="text-muted small">Results for "<c:out value="${param.search}"/>"</span>
                </c:if>
            </div>

            <c:choose>
                <c:when test="${empty products}">
                    <div class="card grid-empty text-center p-5">
                        <svg xmlns="http://www.w3.org/2000/svg" width="56" height="56" fill="#94a3b8" class="mx-auto mb-3" viewBox="0 0 16 16">
                            <path d="M2 0a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V2a2 2 0 0 0-2-2H2zm11.5 1.5.293.293-5.5 5.5a.5.5 0 0 1-.586 0l-2-2a.5.5 0 0 1 .586-.708l1.707 1.707 5.207-5.207a.5.5 0 0 1 .586 0l.707.708L13.5 1.5z"/>
                        </svg>
                        <h5 class="fw-bold">No products match your filters</h5>
                        <p class="text-muted mb-3">Try different keywords or clear the filters.</p>
                        <div>
                            <a href="${pageContext.request.contextPath}/products" class="btn btn-brand">Clear all filters</a>
                        </div>
                    </div>
                </c:when>
                <c:otherwise>
                    <div class="row g-4">
                        <c:forEach var="p" items="${products}">
                            <div class="col-md-4 col-sm-6">
                                <div class="card product-card">
                                    <a href="${pageContext.request.contextPath}/products?id=${p.productId}" class="product-media text-decoration-none">
                                        <c:choose>
                                            <c:when test="${p.hasImage()}">
                                                <img src="${pageContext.request.contextPath}/${p.imageUrl}" alt="${p.name}">
                                            </c:when>
                                            <c:otherwise>
                                                <svg class="icon-ph" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" fill="currentColor">
                                                    <path d="M13 1a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9v2h2a.5.5 0 0 1 0 1H5a.5.5 0 0 1 0-1h2v-2H3a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1h10z"/>
                                                </svg>
                                            </c:otherwise>
                                        </c:choose>
                                    </a>
                                    <div class="card-body">
                                        <h6 class="p-title">
                                            <a href="${pageContext.request.contextPath}/products?id=${p.productId}">
                                                <c:out value="${p.name}"/>
                                            </a>
                                        </h6>
                                        <p class="p-meta mb-0">
                                            <c:out value="${p.brandName}"/>
                                            &middot;
                                            <c:out value="${p.categoryName}"/>
                                        </p>
                                        <div class="d-flex justify-content-between align-items-center mt-2">
                                            <span class="product-price money">$<fmt:formatNumber value="${p.price}" pattern="#,##0.00"/></span>
                                            <span class="p-stock p-stock-${p.status.name() == 'OUT_OF_STOCK' ? 'out' : p.status.name() == 'LOW_STOCK' ? 'low' : 'in'}">
                                                <span class="dot"></span>
                                                <c:choose>
                                                    <c:when test="${p.status.name() == 'OUT_OF_STOCK'}">Out of stock</c:when>
                                                    <c:when test="${p.status.name() == 'LOW_STOCK'}">Low stock</c:when>
                                                    <c:otherwise>In stock</c:otherwise>
                                                </c:choose>
                                            </span>
                                        </div>
                                    </div>
                                    <div class="card-footer">
                                        <c:choose>
                                            <c:when test="${p.stockQuantity > 0}">
                                                <form method="post" action="${pageContext.request.contextPath}/cart/add">
                                                    <input type="hidden" name="productId" value="${p.productId}">
                                                    <input type="hidden" name="quantity" value="1">
                                                    <button type="submit" class="btn-add">
                                                        <svg viewBox="0 0 16 16" fill="currentColor">
                                                            <path d="M0 1.5A.5.5 0 0 1 .5 1H2a.5.5 0 0 1 .485.379L2.89 3H14.5a.5.5 0 0 1 .491.592l-1.5 8A.5.5 0 0 1 13 12H4a.5.5 0 0 1-.491-.408L2.01 3.607 1.61 2H.5a.5.5 0 0 1-.5-.5zM5 12a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm7 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm-7 1a1 1 0 1 1 0 2 1 1 0 0 1 0-2zm7 0a1 1 0 1 1 0 2 1 1 0 0 1 0-2z"/>
                                                        </svg>
                                                        Add to cart
                                                    </button>
                                                </form>
                                            </c:when>
                                            <c:otherwise>
                                                <button class="btn-add" disabled>Unavailable</button>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>
                            </div>
                        </c:forEach>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
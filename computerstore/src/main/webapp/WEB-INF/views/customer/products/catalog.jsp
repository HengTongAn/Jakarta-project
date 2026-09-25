<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Browse Products - Apach_PC/STORE"/>
<%@ include file="../../layouts/header.jspf" %>

<c:set var="totalCount" value="0"/>
<c:forEach var="entry" items="${brandCounts}">
    <c:set var="totalCount" value="${totalCount + entry.value}"/>
</c:forEach>

<section class="hero">
    <span class="hero-orb hero-orb-1"></span>
    <span class="hero-orb hero-orb-2"></span>
    <div class="container">
        <div class="hero-layout">
            <div class="hero-inner">
                <span class="hero-badge"><span class="dot"></span> Curated tech, ready to ship</span>
                <h1 class="display-4">Build your <span class="hero-grad-text">dream PC</span> today</h1>
                <p class="lead mb-4">
                    Hand-picked computer parts and ready-made systems for work, study and gaming.
                </p>

                <form method="get" action="${pageContext.request.contextPath}/products" class="search-bar" role="search">
                    <select name="category" class="search-cat" aria-label="Choose a category">
                        <option value="">All categories</option>
                        <c:forEach var="cat" items="${categories}">
                            <option value="${cat.categoryId}"
                                    <c:if test="${selectedCategoryIds.size() == 1 && selectedCategoryIds.contains(cat.categoryId)}">selected</c:if>>
                                <c:out value="${cat.name}"/>
                            </option>
                        </c:forEach>
                    </select>
                    <input type="search" name="search" class="search-input"
                           placeholder="Search parts, brands, descriptions and more"
                           value="<c:out value='${param.search}'/>" autocomplete="off">
                    <input type="number" name="minPrice" class="search-price" placeholder="Min $"
                           value="<c:out value='${param.minPrice}'/>" min="0" step="0.01" aria-label="Minimum price">
                    <input type="number" name="maxPrice" class="search-price" placeholder="Max $"
                           value="<c:out value='${param.maxPrice}'/>" min="0" step="0.01" aria-label="Maximum price">
                    <button type="submit" class="search-btn" aria-label="Search">
                        <i class="bi bi-search"></i>
                    </button>
                </form>

                <div class="hero-actions">
                    <a class="btn btn-hero" href="#catalog">Explore collection <i class="bi bi-arrow-right ms-1"></i></a>
                    <span class="hero-note"><i class="bi bi-stars"></i> Quality checked by our team</span>
                </div>

                <p class="hero-trust small mt-3">
                    <i class="bi bi-truck"></i> Free delivery over $50
                    <span class="sep">&middot;</span>
                    <i class="bi bi-arrow-counterclockwise"></i> 30-day returns
                    <span class="sep">&middot;</span>
                    <i class="bi bi-shield-check"></i> Buy with confidence
                </p>
            </div>
            <div class="hero-showcase" aria-label="Featured performance setup">
                <div class="hero-showcase-glow"></div>
                <div class="hero-window">
                    <div class="hero-window-bar"><span></span><span></span><span></span><b>APACH_PC/STORE / BUILD LAB</b></div>
                    <div class="hero-screen">
                        <div class="hero-screen-grid"></div>
                        <div class="hero-chip-float"><i class="bi bi-lightning-charge-fill"></i> Performance pick</div>
                        <div class="hero-screen-copy"><small>BUILD BETTER</small><strong>Power<br><em>without limits.</em></strong></div>
                        <div class="hero-screen-stat"><b>98%</b><span>customer rating</span></div>
                    </div>
                    <div class="hero-window-base"></div>
                </div>
                <div class="hero-float-card hero-float-card-top"><i class="bi bi-cpu"></i><span>Next-gen components</span><b>2025</b></div>
                <div class="hero-float-card hero-float-card-bottom"><i class="bi bi-shield-check"></i><span>Warranty included</span></div>
            </div>
        </div>
    </div>
</section>

<c:if test="${empty activeFilters}">
    <section class="benefits-strip" aria-label="Shopping benefits">
        <div class="container">
            <div class="row g-3">
                <div class="col-6 col-lg-3" data-reveal style="--reveal-delay: 40ms"><div class="benefit-item"><i class="bi bi-truck"></i><div><strong>Fast delivery</strong><span>Free over $50</span></div></div></div>
                <div class="col-6 col-lg-3" data-reveal style="--reveal-delay: 100ms"><div class="benefit-item"><i class="bi bi-shield-check"></i><div><strong>Warranty included</strong><span>Shop with confidence</span></div></div></div>
                <div class="col-6 col-lg-3" data-reveal style="--reveal-delay: 160ms"><div class="benefit-item"><i class="bi bi-arrow-counterclockwise"></i><div><strong>30-day returns</strong><span>Simple and stress-free</span></div></div></div>
                <div class="col-6 col-lg-3" data-reveal style="--reveal-delay: 220ms"><div class="benefit-item"><i class="bi bi-headset"></i><div><strong>Expert support</strong><span>Advice when you need it</span></div></div></div>
            </div>
        </div>
    </section>

    <section class="store-section container" aria-labelledby="categories-title">
        <div class="section-heading"><div><span class="eyebrow">Find your next upgrade</span><h2 id="categories-title">Shop by category</h2></div><a href="#catalog" class="section-link">View all products <i class="bi bi-arrow-right"></i></a></div>
        <div class="row g-3">
            <c:forEach var="cat" items="${categories}">
                <div class="col-6 col-md-4 col-lg-2">
                    <a class="category-tile" href="${pageContext.request.contextPath}/products?category=${cat.categoryId}">
                        <span class="category-icon"><i class="bi bi-cpu"></i></span>
                        <strong><c:out value="${cat.name}"/></strong>
                        <small>${empty categoryCounts[cat.categoryId] ? 0 : categoryCounts[cat.categoryId]} products</small>
                    </a>
                </div>
            </c:forEach>
        </div>
    </section>

    <section class="store-section container" aria-labelledby="featured-title">
        <div class="section-heading"><div><span class="eyebrow">Popular with our customers</span><h2 id="featured-title">Featured products</h2></div><a href="#catalog" class="section-link">Browse the collection <i class="bi bi-arrow-right"></i></a></div>
        <div class="row g-4">
            <c:forEach var="p" items="${trending}" end="3"><div class="col-6 col-sm-6 col-lg-3"><%@ include file="../../components/product-card.jspf" %></div></c:forEach>
        </div>
    </section>

    <section class="store-section container pt-3" aria-labelledby="trending-title">
        <div class="section-heading"><div><span class="eyebrow">Based on real orders</span><h2 id="trending-title"><i class="bi bi-fire text-warning"></i> Trending now</h2></div><a href="#catalog" class="section-link">Shop all products <i class="bi bi-arrow-right"></i></a></div>
        <div class="trending-row">
            <c:forEach var="p" items="${trending}"><div class="trending-col"><%@ include file="../../components/product-card.jspf" %></div></c:forEach>
        </div>
    </section>
</c:if>

<div id="catalog" class="container py-4">
    <c:url var="allUrl" value="/products">
        <c:if test="${not empty param.search}"><c:param name="search" value="${param.search}"/></c:if>
        <c:if test="${not empty sort}"><c:param name="sort" value="${sort}"/></c:if>
    </c:url>
    <div class="pill-strip mb-4" role="list" aria-label="Brands">
        <a class="pill ${empty selectedBrandIds ? 'pill-active' : ''}" href="<c:out value='${allUrl}'/>">
            All <em>${totalCount}</em>
        </a>
        <c:forEach var="br" items="${brands}">
            <c:url var="pillUrl" value="/products">
                <c:if test="${not empty param.search}"><c:param name="search" value="${param.search}"/></c:if>
                <c:if test="${not empty sort}"><c:param name="sort" value="${sort}"/></c:if>
                <c:param name="brand" value="${br.brandId}"/>
            </c:url>
            <a class="pill ${selectedBrandIds.contains(br.brandId) ? 'pill-active' : ''}"
               href="<c:out value='${pillUrl}'/>">
                <c:out value="${br.name}"/> <em>${empty brandCounts[br.brandId] ? 0 : brandCounts[br.brandId]}</em>
            </a>
        </c:forEach>
    </div>

    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2">
        <h5 class="mb-0 fw-bold">${products.size()} product${products.size() == 1 ? '' : 's'}</h5>
        <form method="get" action="${pageContext.request.contextPath}/products" class="sort-form">
            <c:if test="${not empty param.search}">
                <input type="hidden" name="search" value="<c:out value='${param.search}'/>">
            </c:if>
            <c:if test="${not empty param.minPrice}">
                <input type="hidden" name="minPrice" value="<c:out value='${param.minPrice}'/>">
            </c:if>
            <c:if test="${not empty param.maxPrice}">
                <input type="hidden" name="maxPrice" value="<c:out value='${param.maxPrice}'/>">
            </c:if>
            <c:forEach var="cat" items="${selectedCategoryIds}">
                <input type="hidden" name="category" value="${cat}">
            </c:forEach>
            <c:forEach var="br" items="${selectedBrandIds}">
                <input type="hidden" name="brand" value="${br}">
            </c:forEach>
            <label for="sortSelect" class="visually-hidden">Sort products</label>
            <select id="sortSelect" name="sort" class="form-select form-select-sm" onchange="this.form.submit()" aria-label="Sort products">
                <option value="" <c:if test="${empty sort}">selected</c:if>>Sort: Name</option>
                <option value="price_asc" <c:if test="${sort == 'price_asc'}">selected</c:if>>Price: Low to High</option>
                <option value="price_desc" <c:if test="${sort == 'price_desc'}">selected</c:if>>Price: High to Low</option>
                <option value="newest" <c:if test="${sort == 'newest'}">selected</c:if>>Newest first</option>
            </select>
        </form>
    </div>

    <c:if test="${not empty activeFilters}">
        <div class="active-chips mb-3">
            <c:forEach var="af" items="${activeFilters}">
                <a class="chip" href="${pageContext.request.contextPath}${af.url}">
                    <c:out value="${af.label}"/>
                    <i class="bi bi-x-lg"></i>
                </a>
            </c:forEach>
            <a class="chip-clear" href="${pageContext.request.contextPath}/products" aria-label="Clear all active filters">
                <i class="bi bi-arrow-counterclockwise" aria-hidden="true"></i><span>Clear all filters</span>
            </a>
        </div>
    </c:if>

    <c:choose>
        <c:when test="${empty products}">
            <div class="card grid-empty text-center p-5">
                <svg xmlns="http://www.w3.org/2000/svg" width="56" height="56" fill="#94a3b8" class="mx-auto mb-3" viewBox="0 0 16 16">
                    <path d="M2 0a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V2a2 2 0 0 0-2-2H2zm11.5 1.5.293.293-5.5 5.5a.5.5 0 0 1-.586 0l-2-2a.5.5 0 0 1 .586-.708l1.707 1.707 5.207-5.207a.5.5 0 0 1 .586 0l.707.708L13.5 1.5z"/>
                </svg>
                <h5 class="fw-bold">No products match your filters</h5>
                <p class="text-muted mb-3">Try different keywords or clear the filters.</p>
                <div>
                    <a href="${pageContext.request.contextPath}/products" class="btn btn-brand btn-clear-filters"><i class="bi bi-arrow-counterclockwise me-1" aria-hidden="true"></i>Clear all filters</a>
                </div>
            </div>
        </c:when>
        <c:otherwise>
            <div class="row g-4">
                <c:forEach var="p" items="${products}">
                    <div class="col-6 col-md-4">
                        <%@ include file="../../components/product-card.jspf" %>
                    </div>
                </c:forEach>
            </div>
        </c:otherwise>
    </c:choose>

    <c:if test="${totalPages > 1}">
        <nav class="mt-4 d-flex justify-content-center" aria-label="Product pages">
            <ul class="pagination pagination-sm">
                <li class="page-item ${page <= 1 ? 'disabled' : ''}">
                    <c:url var="prevUrl" value="/products">
                        <c:if test="${not empty param.search}"><c:param name="search" value="${param.search}"/></c:if>
                        <c:forEach var="cat" items="${selectedCategoryIds}"><c:param name="category" value="${cat}"/></c:forEach>
                        <c:forEach var="br" items="${selectedBrandIds}"><c:param name="brand" value="${br}"/></c:forEach>
                        <c:if test="${not empty param.minPrice}"><c:param name="minPrice" value="${param.minPrice}"/></c:if>
                        <c:if test="${not empty param.maxPrice}"><c:param name="maxPrice" value="${param.maxPrice}"/></c:if>
                        <c:if test="${not empty sort}"><c:param name="sort" value="${sort}"/></c:if>
                        <c:param name="page" value="${page - 1}"/>
                    </c:url>
                    <a class="page-link" href="<c:out value='${prevUrl}'/>" aria-label="Previous page" ${page <= 1 ? 'tabindex="-1" aria-disabled="true"' : ''}>&laquo;</a>
                </li>
                <c:forEach var="i" begin="1" end="${totalPages}">
                    <c:if test="${i >= page - 2 && i <= page + 2}">
                        <li class="page-item ${i == page ? 'active' : ''}">
                            <c:url var="pageUrl" value="/products">
                                <c:if test="${not empty param.search}"><c:param name="search" value="${param.search}"/></c:if>
                                <c:forEach var="cat" items="${selectedCategoryIds}"><c:param name="category" value="${cat}"/></c:forEach>
                                <c:forEach var="br" items="${selectedBrandIds}"><c:param name="brand" value="${br}"/></c:forEach>
                                <c:if test="${not empty param.minPrice}"><c:param name="minPrice" value="${param.minPrice}"/></c:if>
                                <c:if test="${not empty param.maxPrice}"><c:param name="maxPrice" value="${param.maxPrice}"/></c:if>
                                <c:if test="${not empty sort}"><c:param name="sort" value="${sort}"/></c:if>
                                <c:param name="page" value="${i}"/>
                            </c:url>
                            <a class="page-link" href="<c:out value='${pageUrl}'/>">${i}</a>
                        </li>
                    </c:if>
                </c:forEach>
                <li class="page-item ${page >= totalPages ? 'disabled' : ''}">
                    <c:url var="nextUrl" value="/products">
                        <c:if test="${not empty param.search}"><c:param name="search" value="${param.search}"/></c:if>
                        <c:forEach var="cat" items="${selectedCategoryIds}"><c:param name="category" value="${cat}"/></c:forEach>
                        <c:forEach var="br" items="${selectedBrandIds}"><c:param name="brand" value="${br}"/></c:forEach>
                        <c:if test="${not empty param.minPrice}"><c:param name="minPrice" value="${param.minPrice}"/></c:if>
                        <c:if test="${not empty param.maxPrice}"><c:param name="maxPrice" value="${param.maxPrice}"/></c:if>
                        <c:if test="${not empty sort}"><c:param name="sort" value="${sort}"/></c:if>
                        <c:param name="page" value="${page + 1}"/>
                    </c:url>
                    <a class="page-link" href="<c:out value='${nextUrl}'/>" aria-label="Next page" ${page >= totalPages ? 'tabindex="-1" aria-disabled="true"' : ''}>&raquo;</a>
                </li>
            </ul>
        </nav>
    </c:if>
</div>

<c:if test="${empty activeFilters}">
    <section class="store-section container" aria-labelledby="build-title">
        <div class="section-heading"><div><span class="eyebrow">Start with your goal</span><h2 id="build-title">Choose your perfect setup</h2></div></div>
        <div class="row g-4">
            <div class="col-md-4"><a class="build-card build-gaming" href="${pageContext.request.contextPath}/products?search=gaming"><span class="build-icon"><i class="bi bi-controller"></i></span><h3>Gaming setup</h3><p>Fast graphics and smooth performance for every match.</p><span>Explore gaming <i class="bi bi-arrow-right"></i></span></a></div>
            <div class="col-md-4"><a class="build-card build-work" href="${pageContext.request.contextPath}/products?search=workstation"><span class="build-icon"><i class="bi bi-briefcase"></i></span><h3>Workstation</h3><p>Reliable power for creative work, coding, and productivity.</p><span>Build for work <i class="bi bi-arrow-right"></i></span></a></div>
            <div class="col-md-4"><a class="build-card build-study" href="${pageContext.request.contextPath}/products?search=student"><span class="build-icon"><i class="bi bi-book"></i></span><h3>Study setup</h3><p>Smart, practical technology for learning and everyday use.</p><span>Shop for study <i class="bi bi-arrow-right"></i></span></a></div>
        </div>
    </section>

    <section class="promo-banner container" aria-label="Special offer">
        <div><span class="eyebrow">Limited-time offer</span><h2>Upgrade your setup today</h2><p>Get free delivery on orders over $50 and expert help choosing compatible parts.</p></div>
        <a class="btn btn-light" href="#catalog">Shop the offer <i class="bi bi-arrow-right ms-1"></i></a>
    </section>

    <section class="store-section container" aria-labelledby="new-title">
        <div class="section-heading"><div><span class="eyebrow">Just added to Apach_PC/STORE</span><h2 id="new-title">New arrivals</h2></div><a href="#catalog" class="section-link">See all products <i class="bi bi-arrow-right"></i></a></div>
        <div class="row g-4">
            <c:forEach var="p" items="${newArrivals}"><div class="col-6 col-sm-6 col-lg-3"><%@ include file="../../components/product-card.jspf" %></div></c:forEach>
        </div>
    </section>

    <section class="why-section" aria-labelledby="why-title">
        <div class="container"><div class="section-heading"><div><span class="eyebrow">The Apach_PC/STORE promise</span><h2 id="why-title">Why choose us?</h2></div></div><div class="row g-4">
            <div class="col-6 col-md-3"><div class="why-card"><i class="bi bi-patch-check"></i><h3>Quality checked</h3><p>Every product is carefully selected for dependable performance.</p></div></div>
            <div class="col-6 col-md-3"><div class="why-card"><i class="bi bi-diagram-3"></i><h3>Compatible parts</h3><p>Clear product details help you build a system that works together.</p></div></div>
            <div class="col-6 col-md-3"><div class="why-card"><i class="bi bi-chat-heart"></i><h3>Human support</h3><p>Get practical guidance before and after your purchase.</p></div></div>
            <div class="col-6 col-md-3"><div class="why-card"><i class="bi bi-lock"></i><h3>Secure checkout</h3><p>Your account and order information are handled responsibly.</p></div></div>
        </div></div>
    </section>

    <section class="store-section container" aria-labelledby="reviews-title">
        <div class="section-heading"><div><span class="eyebrow">Loved by builders</span><h2 id="reviews-title">What customers say</h2></div></div>
        <c:choose>
            <c:when test="${not empty recentReviews}">
                <div class="row g-4">
                    <c:forEach var="rv" items="${recentReviews}">
                        <div class="col-sm-6 col-md-4"><div class="review-card">
                            <div class="stars"><c:forEach begin="1" end="${rv.rating}">★</c:forEach></div>
                            <p>“<c:out value="${rv.reviewText}"/>”</p>
                            <strong>— <c:out value="${rv.userName}"/></strong>
                            <small>${rv.verified ? 'Verified purchase' : 'Customer review'}</small>
                        </div></div>
                    </c:forEach>
                </div>
            </c:when>
            <c:otherwise>
                <div class="row g-4">
                    <div class="col-sm-6 col-md-4"><div class="review-card"><div class="stars">★★★★★</div><p>“The product details made it easy to choose compatible parts. Everything arrived quickly.”</p><strong>— Daniel R.</strong><small>Verified customer</small></div></div>
                    <div class="col-sm-6 col-md-4"><div class="review-card"><div class="stars">★★★★★</div><p>“Excellent selection and very helpful support. My new workstation runs perfectly.”</p><strong>— Maria S.</strong><small>Verified customer</small></div></div>
                    <div class="col-sm-6 col-md-4"><div class="review-card"><div class="stars">★★★★★</div><p>“Simple checkout, clear stock information, and fast delivery. I will shop here again.”</p><strong>— Alex K.</strong><small>Verified customer</small></div></div>
                </div>
            </c:otherwise>
        </c:choose>
    </section>

    <section class="faq-section" aria-labelledby="faq-title"><div class="container"><div class="section-heading"><div><span class="eyebrow">Need to know</span><h2 id="faq-title">Frequently asked questions</h2></div></div><div class="accordion accordion-clean" id="storeFaq">
        <div class="accordion-item"><h3 class="accordion-header"><button class="accordion-button" data-bs-toggle="collapse" data-bs-target="#faqOne">Do you offer delivery?</button></h3><div id="faqOne" class="accordion-collapse collapse show" data-bs-parent="#storeFaq"><div class="accordion-body">Yes. Delivery is available on all orders, with free delivery over $50.</div></div></div>
        <div class="accordion-item"><h3 class="accordion-header"><button class="accordion-button collapsed" data-bs-toggle="collapse" data-bs-target="#faqTwo">Can I return a product?</button></h3><div id="faqTwo" class="accordion-collapse collapse" data-bs-parent="#storeFaq"><div class="accordion-body">Eligible products can be returned within 30 days. Please contact support before sending an item back.</div></div></div>
        <div class="accordion-item"><h3 class="accordion-header"><button class="accordion-button collapsed" data-bs-toggle="collapse" data-bs-target="#faqThree">How do I know which parts are compatible?</button></h3><div id="faqThree" class="accordion-collapse collapse" data-bs-parent="#storeFaq"><div class="accordion-body">Check the product specifications or contact our support team for help selecting compatible components.</div></div></div>
    </div></div></section>

    <section class="support-cta container" aria-labelledby="support-title"><div><span class="eyebrow">We are here to help</span><h2 id="support-title">Not sure what to buy?</h2><p>Tell us what you want to build and our team can help you choose the right setup.</p></div><a class="btn btn-brand" href="${pageContext.request.contextPath}${empty sessionScope.user ? '/login' : '/mail/compose'}">Talk to our team <i class="bi bi-arrow-right ms-1"></i></a></section>
</c:if>
<script>
    // When arriving with active search/filters, jump straight to the
    // catalog so the results are visible without scrolling past the hero.
    (function () {
        var catalog = document.getElementById('catalog');
        if (!catalog || !${not empty activeFilters}) {
            return;
        }
        var root = document.documentElement;
        var prev = root.style.scrollBehavior;
        root.style.scrollBehavior = 'auto';
        try {
            catalog.scrollIntoView({ block: 'start' });
        } finally {
            root.style.scrollBehavior = prev;
        }
    })();
</script>
<%@ include file="../../layouts/footer.jspf" %>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="${product.name} - TechStore"/>
<%@ include file="../common/header.jspf" %>
<div class="container py-4">
    <div class="card card-hover">
        <div class="row g-0">
            <div class="col-md-5 d-flex align-items-center justify-content-center p-4"
                 style="background:#e2e8f0; min-height:320px;">
                <c:choose>
                    <c:when test="${product.hasImage()}">
                        <img src="${pageContext.request.contextPath}/${product.imageUrl}" alt="${product.name}" 
                             class="img-fluid" style="max-height: 300px; object-fit: contain;">
                    </c:when>
                    <c:otherwise>
                        <svg xmlns="http://www.w3.org/2000/svg" width="120" height="120" fill="#94a3b8" viewBox="0 0 16 16">
                            <path d="M13 1a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9v2h2a.5.5 0 0 1 0 1H5a.5.5 0 0 1 0-1h2v-2H3a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1h10z"/>
                        </svg>
                    </c:otherwise>
                </c:choose>
            </div>
            <div class="col-md-7">
                <div class="card-body p-4">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h3 class="fw-bold mb-1"><c:out value="${product.name}"/></h3>
                            <p class="text-muted small mb-2">
                                SKU: <c:out value="${product.sku}"/>
                                &middot; <c:out value="${product.categoryName}"/>
                                &middot; <c:out value="${product.brandName}"/>
                            </p>
                        </div>
                        <c:choose>
                            <c:when test="${product.status.name() == 'OUT_OF_STOCK'}">
                                <span class="badge bg-danger badge-status">Out of stock</span>
                            </c:when>
                            <c:when test="${product.status.name() == 'LOW_STOCK'}">
                                <span class="badge bg-warning text-dark badge-status">Low stock</span>
                            </c:when>
                            <c:otherwise>
                                <span class="badge bg-success badge-status">In stock</span>
                            </c:otherwise>
                        </c:choose>
                    </div>

                    <div class="my-3">
                        <span class="product-price" style="font-size:1.7rem">$<fmt:formatNumber value="${product.price}" pattern="#,##0.00"/></span>
                    </div>

                    <p class="text-muted"><c:out value="${product.description}"/></p>

                    <div class="order-summary-box p-3 mb-3">
                        <div class="d-flex justify-content-between small">
                            <span>Availability</span>
                            <span class="fw-semibold">${product.stockQuantity > 0 ? product.stockQuantity : '0'} unit(s)</span>
                        </div>
                    </div>

                    <c:choose>
                        <c:when test="${product.stockQuantity > 0}">
                            <c:choose>
                                <c:when test="${not empty sessionScope.user}">
                                    <form method="post" action="${pageContext.request.contextPath}/cart/add" class="d-flex gap-2">
                                        <input type="hidden" name="productId" value="${product.productId}">
                                        <input type="number" name="quantity" value="1" min="1" max="${product.stockQuantity}"
                                               class="form-control" style="width:100px">
                                        <button type="submit" class="btn btn-brand btn-lg flex-grow-1">Add to cart</button>
                                    </form>
                                </c:when>
                                <c:otherwise>
                                    <a class="btn btn-brand btn-lg" href="${pageContext.request.contextPath}/login">Login to buy</a>
                                </c:otherwise>
                            </c:choose>
                        </c:when>
                        <c:otherwise>
                            <button class="btn btn-outline-secondary btn-lg w-100" disabled>Currently unavailable</button>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="${product.name} - Apach_PC/STORE"/>
<%@ include file="../../layouts/header.jspf" %>
<div class="container py-4">
    <nav aria-label="Breadcrumb" class="mb-3">
        <ol class="breadcrumb small mb-0">
            <li class="breadcrumb-item"><a href="${pageContext.request.contextPath}/products" class="text-decoration-none">Home</a></li>
            <li class="breadcrumb-item"><a href="${pageContext.request.contextPath}/products?category=${product.categoryId}" class="text-decoration-none"><c:out value="${product.categoryName}"/></a></li>
            <li class="breadcrumb-item active" aria-current="page"><c:out value="${product.name}"/></li>
        </ol>
    </nav>
    <div class="card card-hover">
        <div class="row g-0">
            <div class="col-md-5 d-flex align-items-center justify-content-center p-4 product-image-panel"
                 style="min-height:320px;">
                <c:choose>
                    <c:when test="${product.hasImage()}">
                        <img src="${pageContext.request.contextPath}/${product.imageUrl}" alt="<c:out value='${product.name}'/>"
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
                                <span class="badge bg-danger badge-status" data-stock-badge-for="${product.productId}">Out of stock</span>
                            </c:when>
                            <c:when test="${product.status.name() == 'LOW_STOCK'}">
                                <span class="badge bg-warning text-dark badge-status" data-stock-badge-for="${product.productId}">Low stock</span>
                            </c:when>
                            <c:otherwise>
                                <span class="badge bg-success badge-status" data-stock-badge-for="${product.productId}">In stock</span>
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
                            <span class="fw-semibold" data-avail-for="${product.productId}">${product.stockQuantity > 0 ? product.stockQuantity : '0'} unit(s)</span>
                        </div>
                    </div>

                    <c:choose>
                        <c:when test="${product.stockQuantity > 0}">
                            <c:choose>
                                <c:when test="${not empty sessionScope.user}">
                                    <form method="post" action="${pageContext.request.contextPath}/cart/add" data-quickadd data-no-spinner data-addform="${product.productId}" class="d-flex gap-2 flex-wrap">
                                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                                        <input type="hidden" name="productId" value="${product.productId}">
                                        <div class="input-group input-group-lg qty-stepper" style="width:140px">
                                            <button type="button" class="btn btn-outline-secondary" data-step="-1" data-target="qtyInput" aria-label="Decrease quantity">
                                                <i class="bi bi-dash-lg" aria-hidden="true"></i>
                                            </button>
                                            <input type="number" id="qtyInput" name="quantity" value="1" min="1" max="${product.stockQuantity}"
                                                       data-qtymax="${product.productId}" class="form-control text-center" aria-label="Quantity">
                                            <button type="button" class="btn btn-outline-secondary" data-step="1" data-target="qtyInput" aria-label="Increase quantity">
                                                <i class="bi bi-plus-lg" aria-hidden="true"></i>
                                            </button>
                                        </div>
                                        <button type="submit" class="btn btn-brand btn-lg flex-grow-1" data-loading="Adding…">Add to cart</button>
                                    </form>
                                    <c:if test="${product.stockQuantity <= 10}">
                                        <p class="small text-danger fw-semibold mt-2 mb-0" data-plow-for="${product.productId}">
                                            <i class="bi bi-hourglass-split" aria-hidden="true"></i>
                                            Only ${product.stockQuantity} left in stock
                                        </p>
                                    </c:if>
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

    <c:if test="${not empty product.highlights or not empty product.specs or not empty product.boxContents or not empty product.warrantyInfo or not empty product.sourceUrl}">
        <div class="card card-hover mt-4">
            <div class="card-body p-4">
                <h5 class="fw-bold mb-3"><i class="bi bi-info-circle me-1" aria-hidden="true"></i> Product details</h5>

                <c:if test="${not empty product.highlights}">
                    <h6 class="fw-semibold mb-2">Key features</h6>
                    <ul class="product-highlights mb-4">
                        <c:forEach var="hl" items="${product.highlights}">
                            <li><c:out value="${hl}"/></li>
                        </c:forEach>
                    </ul>
                </c:if>

                <c:if test="${not empty product.specs}">
                    <h6 class="fw-semibold mb-2">Specifications</h6>
                    <div class="table-responsive mb-4">
                        <table class="table table-striped product-specs-table">
                            <tbody>
                                <c:forEach var="spec" items="${product.specs}">
                                    <tr>
                                        <th scope="row" style="width:40%"><c:out value="${spec.specKey}"/></th>
                                        <td><c:out value="${spec.specValue}"/></td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:if>

                <c:if test="${not empty product.boxContents}">
                    <h6 class="fw-semibold mb-1">What's in the box</h6>
                    <p class="text-muted"><c:out value="${product.boxContents}"/></p>
                </c:if>

                <c:if test="${not empty product.warrantyInfo}">
                    <h6 class="fw-semibold mb-1">Warranty</h6>
                    <p class="text-muted"><c:out value="${product.warrantyInfo}"/></p>
                </c:if>

                <c:if test="${not empty product.sourceUrl}">
                    <p class="small mb-0">
                        <i class="bi bi-box-arrow-up-right me-1" aria-hidden="true"></i>
                        <a href="<c:out value='${product.sourceUrl}'/>" target="_blank" rel="noopener noreferrer"
                           class="text-decoration-none">
                            Official product page
                        </a>
                    </p>
                </c:if>
            </div>
        </div>
    </c:if>

    <div class="card card-hover mt-4">
        <div class="card-body p-4">
            <div class="d-flex flex-wrap justify-content-between align-items-start gap-3">
                <div>
                    <h5 class="fw-bold mb-1"><i class="bi bi-star me-1" aria-hidden="true"></i> Customer reviews</h5>
                    <c:choose>
                        <c:when test="${ratingSummary.hasReviews()}">
                            <div class="d-flex align-items-center gap-2 mt-1">
                                <span class="fs-3 fw-bold">${ratingSummary.average}</span>
                                <span class="text-warning fs-5">
                                    <c:forEach begin="1" end="${ratingSummary.displayStars()}">★</c:forEach><c:forEach begin="${ratingSummary.displayStars() + 1}" end="5">☆</c:forEach>
                                </span>
                                <span class="text-muted small">based on ${ratingSummary.count} review(s)</span>
                            </div>
                        </c:when>
                        <c:otherwise>
                            <p class="text-muted mb-0 mt-1">No reviews yet — be the first to review this product.</p>
                        </c:otherwise>
                    </c:choose>
                </div>
                <c:choose>
                    <c:when test="${empty sessionScope.user}">
                        <a class="btn btn-outline-primary" href="${pageContext.request.contextPath}/login">Log in to write a review</a>
                    </c:when>
                    <c:when test="${not empty myReview}">
                        <span class="badge${myReview.status == 'APPROVED' ? ' bg-success' : myReview.status == 'REJECTED' ? ' bg-danger' : ' bg-warning text-dark'}">
                            <i class="bi bi-check-circle me-1" aria-hidden="true"></i>
                            Your review: ${myReview.status == 'APPROVED' ? 'published' : myReview.status == 'REJECTED' ? 'not approved' : 'awaiting approval'}
                        </span>
                    </c:when>
                    <c:otherwise>
                        <button type="button" class="btn btn-brand" data-bs-toggle="collapse" data-bs-target="#writeReviewForm">Write a review</button>
                    </c:otherwise>
                </c:choose>
            </div>

            <c:if test="${empty sessionScope.user or empty myReview}">
                <c:if test="${not empty sessionScope.user}">
                    <form method="post" action="${pageContext.request.contextPath}/products/review" id="writeReviewForm" class="review-form border rounded p-3 mt-4 collapse${empty param.showForm ? '' : ' show'}">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="productId" value="${product.productId}">
                        <div class="mb-3">
                            <span class="fw-semibold small">Your rating</span>
                            <div class="star-input">
                                <c:forEach begin="1" end="5" var="i">
                                    <label class="star-option" title="${i} star${i == 1 ? '' : 's'}">
                                        <input type="radio" name="rating" value="${i}" ${i == 5 ? 'checked' : ''}>
                                        <span class="star-label">★</span>
                                    </label>
                                </c:forEach>
                            </div>
                        </div>
                        <div class="mb-3">
                            <input type="text" name="title" class="form-control" maxlength="150"
                                   placeholder="Review title (optional)">
                        </div>
                        <div class="mb-3">
                            <textarea name="reviewText" class="form-control" rows="4" maxlength="4000" required
                                      placeholder="Tell others what you thought of this product…"></textarea>
                        </div>
                        <button type="submit" class="btn btn-brand" data-loading="Submitting…">Submit review</button>
                    </form>
                </c:if>
            </c:if>

            <c:if test="${not empty reviews}">
                <div class="mt-4 d-flex flex-column gap-3">
                    <c:forEach var="rv" items="${reviews}">
                        <div class="border rounded p-3">
                            <div class="d-flex flex-wrap justify-content-between align-items-center gap-2">
                                <div>
                                    <span class="fw-semibold"><c:out value="${rv.userName}"/></span>
                                    <c:if test="${rv.verified}">
                                        <span class="badge bg-success-subtle text-success border ms-1">
                                            <i class="bi bi-patch-check-fill me-1" aria-hidden="true"></i>Verified purchase
                                        </span>
                                    </c:if>
                                </div>
                                <span class="text-warning small">
                                    <c:forEach begin="1" end="${rv.rating}">★</c:forEach><c:forEach begin="${rv.rating + 1}" end="5">☆</c:forEach>
                                </span>
                            </div>
                            <c:if test="${not empty rv.title}">
                                <div class="fw-semibold mt-2"><c:out value="${rv.title}"/></div>
                            </c:if>
                            <p class="text-muted mb-1 mt-1"><c:out value="${rv.reviewText}"/></p>
                            <div class="small text-muted"><fmt:formatDate value="${rv.createdAt}" pattern="dd MMM yyyy"/></div>
                        </div>
                    </c:forEach>
                </div>
            </c:if>
        </div>
    </div>
</div>
<%@ include file="../../layouts/footer.jspf" %>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Shopping Cart - TechStore"/>
<%@ include file="../../layouts/header.jspf" %>
<div class="container py-4">
    <h4 class="fw-bold mb-1">Shopping Cart</h4>
    <c:if test="${not empty items}">
        <div class="checkout-steps mb-4" aria-label="Checkout progress">
            <span class="step done"><i class="bi bi-cart-check-fill"></i><span class="d-none d-sm-inline"> Cart</span></span>
            <span class="step-bar done"></span>
            <span class="step"><i class="bi bi-credit-card-2-front"></i><span class="d-none d-sm-inline"> Checkout</span></span>
            <span class="step-bar"></span>
            <span class="step"><i class="bi bi-check-circle"></i><span class="d-none d-sm-inline"> Confirmed</span></span>
        </div>
    </c:if>

    <c:choose>
        <c:when test="${empty items}">
            <div class="card text-center p-5 empty-state">
                <span class="empty-icon"><i class="bi bi-cart3"></i></span>
                <h5 class="fw-bold">Your cart is empty</h5>
                <p class="text-muted mb-3">Browse the catalogue and add some products to get started.</p>
                <div>
                    <a href="${pageContext.request.contextPath}/products" class="btn btn-brand">Start shopping</a>
                </div>
            </div>
        </c:when>
        <c:otherwise>
            <div class="row g-3">
                <div class="col-lg-8">
                    <div class="card card-hover">
                        <div class="table-responsive d-none d-sm-block">
                            <table class="table align-middle mb-0 cart-table">
                                <thead class="table-light">
                                <tr>
                                    <th style="width: 80px;">Image</th>
                                    <th>Product</th>
                                    <th class="text-center">Price</th>
                                    <th class="text-center" style="width:160px">Quantity</th>
                                    <th class="text-end">Subtotal</th>
                                    <th></th>
                                </tr>
                                </thead>
                                <tbody>
                                <c:forEach var="item" items="${items}">
                                    <tr>
                                        <td>
                                            <c:choose>
                                                <c:when test="${item.product.hasImage()}">
                                                    <img src="${pageContext.request.contextPath}/${item.product.imageUrl}" alt="<c:out value='${item.product.name}'/>" 
                                                         class="img-thumbnail" style="width: 60px; height: 60px; object-fit: cover;">
                                                </c:when>
                                                <c:otherwise>
                                                    <div class="bg-secondary d-flex align-items-center justify-content-center" style="width: 60px; height: 60px; border-radius: 4px;">
                                                        <span class="text-white small">No img</span>
                                                    </div>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td>
                                            <a href="${pageContext.request.contextPath}/products?id=${item.product.productId}" class="text-decoration-none fw-semibold">
                                                <c:out value="${item.product.name}"/>
                                            </a>
                                            <div class="small text-muted">
                                                <c:out value="${item.product.brandName}"/>
                                                <c:if test="${item.quantity > item.product.stockQuantity}">
                                                    <span class="badge bg-danger ms-2">Stock reduced</span>
                                                </c:if>
                                            </div>
                                        </td>
                                        <td class="text-center money">$<fmt:formatNumber value="${item.product.price}" pattern="#,##0.00"/></td>
                                        <td>
                                            <form method="post" action="${pageContext.request.contextPath}/cart/update" class="d-flex flex-column align-items-center gap-1" data-no-spinner>
                                                <input type="hidden" name="csrfToken" value="${csrfToken}">
                                                <input type="hidden" name="cartItemId" value="${item.cartItemId}">
                                                <div class="input-group input-group-sm cart-qty">
                                                    <button type="button" class="btn btn-outline-secondary" data-step="-1" data-target="qty-${item.cartItemId}" data-submit="true" aria-label="Decrease quantity">
                                                        <i class="bi bi-dash-lg" aria-hidden="true"></i>
                                                    </button>
                                                    <input type="number" id="qty-${item.cartItemId}" name="quantity" value="${item.quantity}" min="1"
                                                           max="${item.product.stockQuantity}" class="form-control text-center" aria-label="Quantity for ${item.product.name}">
                                                    <button type="button" class="btn btn-outline-secondary" data-step="1" data-target="qty-${item.cartItemId}" data-submit="true" aria-label="Increase quantity">
                                                        <i class="bi bi-plus-lg" aria-hidden="true"></i>
                                                    </button>
                                                </div>
                                                <button type="submit" class="btn btn-link btn-sm p-0 d-none">Update</button>
                                                <c:if test="${item.quantity < item.product.stockQuantity}">
                                                    <div class="small text-muted">${item.product.stockQuantity} available</div>
                                                </c:if>
                                            </form>
                                        </td>
                                        <td class="text-end money fw-semibold">
                                            $<fmt:formatNumber value="${item.product.price * item.quantity}" pattern="#,##0.00"/>
                                        </td>
                                        <td class="text-end">
                                            <form method="post" action="${pageContext.request.contextPath}/cart/remove" class="cart-remove-form">
                                                <input type="hidden" name="csrfToken" value="${csrfToken}">
                                                <input type="hidden" name="cartItemId" value="${item.cartItemId}">
                                                <button type="submit" class="btn btn-outline-danger btn-sm" data-confirm>Remove</button>
                                            </form>
                                        </td>
                                    </tr>
                                </c:forEach>
                                </tbody>
                            </table>
                        </div>

                        <!-- Phone layout: each cart line as a stacked card instead of the 650px-wide table. -->
                        <div class="d-sm-none d-flex flex-column gap-3">
                            <c:forEach var="item" items="${items}">
                                <div class="card card-hover">
                                    <div class="card-body p-3">
                                        <div class="d-flex gap-3">
                                            <c:choose>
                                                <c:when test="${item.product.hasImage()}">
                                                    <img src="${pageContext.request.contextPath}/${item.product.imageUrl}" alt="<c:out value='${item.product.name}'/>"
                                                         class="rounded" style="width:76px; height:76px; object-fit: cover; flex-shrink:0;">
                                                </c:when>
                                                <c:otherwise>
                                                    <div class="bg-secondary d-flex align-items-center justify-content-center rounded"
                                                         style="width:76px; height:76px; flex-shrink:0;">
                                                        <span class="text-white small">No img</span>
                                                    </div>
                                                </c:otherwise>
                                            </c:choose>
                                            <div class="flex-grow-1 min-w-0">
                                                <a href="${pageContext.request.contextPath}/products?id=${item.product.productId}"
                                                   class="text-decoration-none fw-semibold d-inline-block">
                                                    <c:out value="${item.product.name}"/>
                                                </a>
                                                <div class="small text-muted"><c:out value="${item.product.brandName}"/></div>
                                                <div class="small text-muted mt-1">$<fmt:formatNumber value="${item.product.price}" pattern="#,##0.00"/> each</div>
                                                <c:if test="${item.quantity > item.product.stockQuantity}">
                                                    <span class="badge bg-danger mt-1">Stock reduced</span>
                                                </c:if>
                                            </div>
                                        </div>
                                        <hr class="my-2">
                                        <div class="d-flex justify-content-between align-items-center gap-2 flex-wrap">
                                            <form method="post" action="${pageContext.request.contextPath}/cart/update"
                                                  class="d-flex flex-column align-items-center gap-1 mb-0" data-no-spinner>
                                                <input type="hidden" name="csrfToken" value="${csrfToken}">
                                                <input type="hidden" name="cartItemId" value="${item.cartItemId}">
                                                <div class="input-group input-group-sm cart-qty">
                                                    <button type="button" class="btn btn-outline-secondary" data-step="-1" data-target="m-qty-${item.cartItemId}" data-submit="true" aria-label="Decrease quantity">
                                                        <i class="bi bi-dash-lg" aria-hidden="true"></i>
                                                    </button>
                                                    <input type="number" id="m-qty-${item.cartItemId}" name="quantity" value="${item.quantity}" min="1"
                                                           max="${item.product.stockQuantity}" class="form-control text-center" aria-label="Quantity for ${item.product.name}">
                                                    <button type="button" class="btn btn-outline-secondary" data-step="1" data-target="m-qty-${item.cartItemId}" data-submit="true" aria-label="Increase quantity">
                                                        <i class="bi bi-plus-lg" aria-hidden="true"></i>
                                                    </button>
                                                </div>
                                                <button type="submit" class="btn btn-link btn-sm p-0 d-none">Update</button>
                                                <c:if test="${item.quantity < item.product.stockQuantity}">
                                                    <div class="small text-muted">${item.product.stockQuantity} available</div>
                                                </c:if>
                                            </form>
                                            <div class="d-flex align-items-center gap-2">
                                                <span class="money fw-semibold">$<fmt:formatNumber value="${item.product.price * item.quantity}" pattern="#,##0.00"/></span>
                                                <form method="post" action="${pageContext.request.contextPath}/cart/remove" class="cart-remove-form mb-0">
                                                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                                                    <input type="hidden" name="cartItemId" value="${item.cartItemId}">
                                                    <button type="submit" class="btn btn-outline-danger btn-sm" data-confirm>Remove</button>
                                                </form>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </div>
                </div>
                <div class="col-lg-4">
                    <div class="card card-hover order-summary-box sticky-top" style="top: 5.5rem;">
                        <div class="card-body">
                            <h6 class="fw-bold">Order summary</h6>
                            <div class="d-flex justify-content-between mb-2">
                                <span>Items</span>
                                <span>${items.size()}</span>
                            </div>
                            <hr>
                            <div class="d-flex justify-content-between fw-bold fs-5">
                                <span>Total</span>
                                <span class="money">$<fmt:formatNumber value="${total}" pattern="#,##0.00"/></span>
                            </div>
                            <a href="${pageContext.request.contextPath}/checkout" class="btn btn-brand w-100 mt-3">Proceed to checkout</a>
                            <a href="${pageContext.request.contextPath}/products" class="btn btn-outline-secondary w-100 mt-2">Continue shopping</a>
                        </div>
                    </div>
                </div>
            </div>
        </c:otherwise>
    </c:choose>
</div>
<%@ include file="../../layouts/footer.jspf" %>

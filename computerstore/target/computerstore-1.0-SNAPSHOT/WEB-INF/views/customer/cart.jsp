<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Shopping Cart - TechStore"/>
<%@ include file="../common/header.jspf" %>
<div class="container py-4">
    <h4 class="fw-bold mb-3">Shopping Cart</h4>

    <c:choose>
        <c:when test="${empty items}">
            <div class="card text-center p-5">
                <h5 class="fw-bold">Your cart is empty</h5>
                <p class="text-muted">Browse the catalogue and add some products.</p>
                <div>
                    <a href="${pageContext.request.contextPath}/products" class="btn btn-brand">Start shopping</a>
                </div>
            </div>
        </c:when>
        <c:otherwise>
            <div class="row g-3">
                <div class="col-lg-8">
                    <div class="card card-hover">
                        <div class="table-responsive">
                            <table class="table align-middle mb-0">
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
                                                    <img src="${pageContext.request.contextPath}/${item.product.imageUrl}" alt="${item.product.name}" 
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
                                            <form method="post" action="${pageContext.request.contextPath}/cart/update" class="d-flex justify-content-center gap-1">
                                                <input type="hidden" name="cartItemId" value="${item.cartItemId}">
                                                <input type="number" name="quantity" value="${item.quantity}" min="1"
                                                       max="${item.product.stockQuantity}" class="form-control form-control-sm text-center" style="width:80px">
                                                <button type="submit" class="btn btn-outline-secondary btn-sm">Update</button>
                                            </form>
                                        </td>
                                        <td class="text-end money fw-semibold">
                                            $<fmt:formatNumber value="${item.product.price * item.quantity}" pattern="#,##0.00"/>
                                        </td>
                                        <td class="text-end">
                                            <form method="post" action="${pageContext.request.contextPath}/cart/remove"
                                                  onsubmit="return confirm('Remove this item from your cart?');">
                                                <input type="hidden" name="cartItemId" value="${item.cartItemId}">
                                                <button type="submit" class="btn btn-outline-danger btn-sm">Remove</button>
                                            </form>
                                        </td>
                                    </tr>
                                </c:forEach>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
                <div class="col-lg-4">
                    <div class="card card-hover order-summary-box">
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
<%@ include file="../common/footer.jspf" %>
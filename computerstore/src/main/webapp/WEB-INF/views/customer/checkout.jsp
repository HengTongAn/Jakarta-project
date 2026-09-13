<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Checkout - TechStore"/>
<%@ include file="../common/header.jspf" %>
<div class="container py-4">
    <h4 class="fw-bold mb-3">Checkout</h4>

    <div class="row g-3">
        <div class="col-lg-8">
            <div class="card card-hover">
                <div class="card-body">
                    <h6 class="fw-bold mb-3">Shipping &amp; billing details</h6>
                    <div class="row g-3">
                        <div class="col-md-6">
                            <label class="form-label">Full name</label>
                            <input type="text" class="form-control" value="${sessionScope.user.fullName}" readonly>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">Email</label>
                            <input type="text" class="form-control" value="${sessionScope.user.email}" readonly>
                        </div>
                        <div class="col-12">
                            <label class="form-label">Delivery address</label>
                            <textarea class="form-control" rows="2" placeholder="Enter your delivery address" disabled></textarea>
                            <div class="form-text">Address capture is not implemented in this demo version.</div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="card card-hover mt-3">
                <div class="card-body p-0">
                    <div class="table-responsive">
                        <table class="table align-middle mb-0">
                            <thead class="table-light">
                            <tr><th>Product</th><th class="text-center">Qty</th><th class="text-center">Unit price</th><th class="text-end">Subtotal</th></tr>
                            </thead>
                            <tbody>
                            <c:forEach var="item" items="${items}">
                                <tr>
                                    <td><c:out value="${item.product.name}"/></td>
                                    <td class="text-center">${item.quantity}</td>
                                    <td class="text-center money">$<fmt:formatNumber value="${item.product.price}" pattern="#,##0.00"/></td>
                                    <td class="text-end money">$<fmt:formatNumber value="${item.product.price * item.quantity}" pattern="#,##0.00"/></td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-lg-4">
            <div class="card card-hover order-summary-box">
                <div class="card-body">
                    <h6 class="fw-bold">Order summary</h6>
                    <div class="d-flex justify-content-between mb-2">
                        <span>Items</span><span>${items.size()}</span>
                    </div>
                    <div class="d-flex justify-content-between mb-2">
                        <span>Shipping</span><span>Free</span>
                    </div>
                    <hr>
                    <div class="d-flex justify-content-between fw-bold fs-5">
                        <span>Total</span>
                        <span class="money">$<fmt:formatNumber value="${total}" pattern="#,##0.00"/></span>
                    </div>
                    <form method="post" action="${pageContext.request.contextPath}/checkout" class="mt-3">
                        <button type="submit" class="btn btn-brand w-100 btn-lg">Place order</button>
                    </form>
                    <a href="${pageContext.request.contextPath}/cart" class="btn btn-outline-secondary w-100 mt-2">Back to cart</a>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
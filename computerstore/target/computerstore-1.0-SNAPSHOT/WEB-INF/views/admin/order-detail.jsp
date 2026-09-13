<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Order #${order.orderId} - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <a href="${pageContext.request.contextPath}/admin/orders" class="btn btn-outline-secondary btn-sm">&larr; All orders</a>
        <c:choose>
            <c:when test="${order.status.name() == 'COMPLETED'}"><span class="badge bg-success badge-status fs-6">${order.status}</span></c:when>
            <c:when test="${order.status.name() == 'CANCELLED'}"><span class="badge bg-secondary badge-status fs-6">${order.status}</span></c:when>
            <c:when test="${order.status.name() == 'PROCESSING'}"><span class="badge bg-info badge-status fs-6">${order.status}</span></c:when>
            <c:otherwise><span class="badge bg-warning text-dark badge-status fs-6">${order.status}</span></c:otherwise>
        </c:choose>
    </div>

    <div class="card card-hover mb-3">
        <div class="card-body">
            <div class="row text-muted small">
                <div class="col-md-3">
                    <div class="fw-semibold text-dark">Order number</div>
                    #${order.orderId}
                </div>
                <div class="col-md-3">
                    <div class="fw-semibold text-dark">Order date</div>
                    <fmt:formatDate value="${order.orderDate}" pattern="dd MMM yyyy HH:mm"/>
                </div>
                <div class="col-md-3">
                    <div class="fw-semibold text-dark">Customer</div>
                    <c:out value="${order.customerName}"/>
                    <div class="text-muted"><c:out value="${order.customerUsername}"/></div>
                </div>
                <div class="col-md-3">
                    <div class="fw-semibold text-dark">Total amount</div>
                    <span class="money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></span>
                </div>
            </div>
        </div>
    </div>

    <div class="card card-hover mb-3">
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr><th>Product</th><th class="text-center">Quantity</th><th class="text-center">Unit price</th><th class="text-end">Subtotal</th></tr>
                </thead>
                <tbody>
                <c:forEach var="item" items="${order.items}">
                    <tr>
                        <td><c:out value="${item.productName}"/></td>
                        <td class="text-center">${item.quantity}</td>
                        <td class="text-center money">$<fmt:formatNumber value="${item.unitPrice}" pattern="#,##0.00"/></td>
                        <td class="text-end money">$<fmt:formatNumber value="${item.subtotal}" pattern="#,##0.00"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
    </div>

    <div class="card card-hover">
        <div class="card-body">
            <h6 class="fw-bold mb-3">Update order status</h6>
            <c:if test="${order.status.name() == 'CANCELLED'}">
                <div class="alert alert-warning py-2 small">
                    This order is cancelled. Products have been returned to stock.
                </div>
            </c:if>
            <form method="post" action="${pageContext.request.contextPath}/admin/orders" class="row g-2 align-items-center">
                <input type="hidden" name="orderId" value="${order.orderId}">
                <div class="col-md-3">
                    <select name="status" class="form-select">
                        <c:forEach var="s" items="${['PENDING','PROCESSING','COMPLETED','CANCELLED']}">
                            <option value="${s}" <c:if test="${order.status.name() == s}">selected</c:if>>${s}</option>
                        </c:forEach>
                    </select>
                </div>
                <div class="col-md-3">
                    <button type="submit" class="btn btn-brand">Update status</button>
                </div>
            </form>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
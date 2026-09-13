<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Order #${order.orderId} - TechStore"/>
<%@ include file="../common/header.jspf" %>
<div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <a href="${pageContext.request.contextPath}/account/orders" class="btn btn-outline-secondary btn-sm">&larr; All orders</a>
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
                    <div class="fw-semibold text-dark">Placed by</div>
                    <c:out value="${empty order.customerName ? sessionScope.user.fullName : order.customerName}"/>
                </div>
                <div class="col-md-3">
                    <div class="fw-semibold text-dark">Total amount</div>
                    <span class="money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></span>
                </div>
            </div>
        </div>
    </div>

    <div class="card card-hover">
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
                <tfoot>
                <tr class="table-light">
                    <td colspan="3" class="text-end fw-bold">Grand total</td>
                    <td class="text-end fw-bold money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></td>
                </tr>
                </tfoot>
            </table>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
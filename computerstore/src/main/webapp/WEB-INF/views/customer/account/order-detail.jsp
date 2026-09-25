<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Order #${order.orderId} - TechStore"/>
<%@ include file="../../layouts/header.jspf" %>
<div class="container py-4" data-order-detail="${order.orderId}" data-current-status="${order.status.name()}">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <a href="${pageContext.request.contextPath}/account/orders" class="btn btn-outline-secondary btn-sm">&larr; All orders</a>
        <span data-order-status="${order.orderId}">
            <c:set var="_statusLabel" value="${order.status}"/>
            <%@ include file="../../components/status-badge.jspf" %>
        </span>
    </div>

    <div class="card card-hover mb-3">
        <div class="card-body">
            <div class="row text-muted small">
                <div class="col-6 col-md-3">
<div class="fw-semibold text-body">Order number</div>                    #${order.orderId}
                </div>
                <div class="col-6 col-md-3">
<div class="fw-semibold text-body">Order date</div>                    <fmt:formatDate value="${order.orderDate}" pattern="dd MMM yyyy HH:mm"/>
                </div>
                <div class="col-6 col-md-3">
<div class="fw-semibold text-body">Placed by</div>                    <c:out value="${empty order.customerName ? sessionScope.user.fullName : order.customerName}"/>
                </div>
                <div class="col-6 col-md-3">
<div class="fw-semibold text-body">Total amount</div>                    <span class="money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></span>
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

    <c:if test="${order.status.name() == 'PENDING'}">
        <div class="card card-hover mb-3 border-warning" data-cancelcard="${order.orderId}">
            <div class="card-body">
                <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
                    <div>
                        <h6 class="fw-bold mb-1">Change your mind?</h6>
                        <p class="text-muted small mb-0">You can still cancel this order while it is pending. The products will be returned to stock.</p>
                    </div>
                    <form method="post" action="${pageContext.request.contextPath}/account/orders" class="d-inline-block"
                          onsubmit="return confirm('Cancel order #${order.orderId}? The products will be returned to stock.');">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="cancel">
                        <input type="hidden" name="orderId" value="${order.orderId}">
                        <button type="submit" class="btn btn-outline-danger">Cancel order</button>
                    </form>
                </div>
            </div>
        </div>
    </c:if>

    <div class="card card-hover">
        <div class="card-body">
            <h6 class="fw-bold mb-3">Status history</h6>
            <c:choose>
                <c:when test="${empty order.statusEvents}">
                    <p class="text-muted mb-0">No status history recorded for this order.</p>
                </c:when>
                <c:otherwise>
                    <ul class="list-group list-group-flush">
                        <c:forEach var="event" items="${order.statusEvents}">
                            <li class="list-group-item px-0">
                                <div class="d-flex flex-wrap justify-content-between align-items-start gap-2">
                                    <div class="flex-grow-1 min-w-0" style="overflow-wrap:anywhere;">
                                        <span class="fw-semibold"><c:out value="${event.changedBy}"/></span>
                                        <span class="text-muted">moved the order to</span>
                                        <c:set var="_statusLabel" value="${event.toStatus}"/>
                                        <%@ include file="../../components/status-badge.jspf" %>
                                        <c:if test="${not empty event.note}">
                                            <div class="small text-muted mt-1"><c:out value="${event.note}"/></div>
                                        </c:if>
                                    </div>
                                    <span class="small text-muted text-nowrap">
                                        <fmt:formatDate value="${event.createdAt}" pattern="dd MMM yyyy HH:mm"/>
                                    </span>
                                </div>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</div>
<%@ include file="../../layouts/footer.jspf" %>

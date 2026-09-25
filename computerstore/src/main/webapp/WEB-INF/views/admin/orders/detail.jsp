<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Order #${order.orderId} - Admin"/>
<%@ include file="../../layouts/header.jspf" %>
<%@ include file="../../layouts/admin-nav.jspf" %>

<div class="container py-4" data-order-detail="${order.orderId}" data-current-status="${order.status.name()}">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <a href="${pageContext.request.contextPath}/admin/orders" class="btn btn-outline-secondary btn-sm">&larr; All orders</a>
        <span data-order-status="${order.orderId}">
            <c:set var="_statusLabel" value="${order.status}"/>
            <%@ include file="../../components/status-badge.jspf" %>
        </span>
    </div>

    <div class="card card-hover mb-3">
        <div class="card-body">
            <div class="row text-muted small">
                <div class="col-md-3">
<div class="fw-semibold text-body">Order number</div>                    #${order.orderId}
                </div>
                <div class="col-md-3">
<div class="fw-semibold text-body">Order date</div>                    <fmt:formatDate value="${order.orderDate}" pattern="dd MMM yyyy HH:mm"/>
                </div>
                <div class="col-md-3">
<div class="fw-semibold text-body">Customer</div>                    <c:out value="${order.customerName}"/>
                    <div class="text-muted"><c:out value="${order.customerUsername}"/></div>
                </div>
                <div class="col-md-3">
<div class="fw-semibold text-body">Total amount</div>                    <span class="money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></span>
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
            <h6 class="fw-bold mb-3">Order actions</h6>
            <c:choose>
                <c:when test="${order.status.name() == 'PENDING'}">
                    <form method="post" action="${pageContext.request.contextPath}/admin/orders" class="d-inline-block me-2">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="orderId" value="${order.orderId}">
                        <input type="hidden" name="status" value="PROCESSING">
                        <button type="submit" class="btn btn-brand">Confirm order</button>
                    </form>
                    <form method="post" action="${pageContext.request.contextPath}/admin/orders" class="d-inline-block"
                          onsubmit="return confirm('Cancel this order? The products will be returned to stock.');">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="orderId" value="${order.orderId}">
                        <input type="hidden" name="status" value="CANCELLED">
                        <button type="submit" class="btn btn-outline-danger">Cancel order</button>
                    </form>
                </c:when>
                <c:when test="${order.status.name() == 'PROCESSING'}">
                    <form method="post" action="${pageContext.request.contextPath}/admin/orders" class="d-inline-block me-2">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="orderId" value="${order.orderId}">
                        <input type="hidden" name="status" value="SHIPPED">
                        <button type="submit" class="btn btn-brand">Mark as shipped</button>
                    </form>
                    <form method="post" action="${pageContext.request.contextPath}/admin/orders" class="d-inline-block"
                          onsubmit="return confirm('Cancel this order? The products will be returned to stock.');">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="orderId" value="${order.orderId}">
                        <input type="hidden" name="status" value="CANCELLED">
                        <button type="submit" class="btn btn-outline-danger">Cancel order</button>
                    </form>
                </c:when>
                <c:when test="${order.status.name() == 'SHIPPED'}">
                    <form method="post" action="${pageContext.request.contextPath}/admin/orders" class="d-inline-block">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="orderId" value="${order.orderId}">
                        <input type="hidden" name="status" value="COMPLETED">
                        <button type="submit" class="btn btn-brand">Mark as delivered</button>
                    </form>
                </c:when>
                <c:when test="${order.status.name() == 'COMPLETED'}">
                    <form method="post" action="${pageContext.request.contextPath}/admin/orders" class="d-inline-block"
                          onsubmit="return confirm('Refund this order? The products will be returned to stock.');">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="orderId" value="${order.orderId}">
                        <input type="hidden" name="status" value="REFUNDED">
                        <button type="submit" class="btn btn-outline-danger">Refund order</button>
                    </form>
                </c:when>
                <c:when test="${order.status.name() == 'CANCELLED'}">
                    <p class="text-muted mb-0">This order is cancelled. Products have been returned to stock.</p>
                </c:when>
                <c:otherwise>
                    <p class="text-muted mb-0">This order is refunded. Products have been returned to stock.</p>
                </c:otherwise>
            </c:choose>
        </div>
    </div>

    <div class="card card-hover mt-3">
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
                                <div class="d-flex justify-content-between align-items-center gap-2">
                                    <div>
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

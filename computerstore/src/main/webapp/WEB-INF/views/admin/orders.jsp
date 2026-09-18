<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Orders - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h4 class="fw-bold mb-0">Orders <span class="badge bg-secondary rounded-pill align-middle" id="ordersCount">${orders.size()}</span></h4>
        <label for="ordersFilter" class="visually-hidden">Filter orders</label>
        <input type="search" id="ordersFilter" class="form-control form-control-sm table-filter" placeholder="Filter orders…">
    </div>

    <div class="card card-hover">
        <div class="table-responsive">
            <table class="table align-middle mb-0" data-sortable data-filter-target="ordersFilter" data-count="ordersCount">
                <thead class="table-light">
                <tr>
                    <th data-sort="number">Order #</th><th>Customer</th><th>Date</th>
                    <th class="text-center" data-sort="number">Items</th>
                    <th class="text-end" data-sort="price">Total</th>
                    <th class="text-center">Status</th>
                    <th class="text-end" data-nosort>Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="order" items="${orders}">
                    <tr>
                        <td>#${order.orderId}</td>
                        <td>
                            <div class="fw-semibold"><c:out value="${order.customerName}"/></div>
                            <div class="small text-muted"><c:out value="${order.customerUsername}"/></div>
                        </td>
                        <td><fmt:formatDate value="${order.orderDate}" pattern="dd MMM yyyy HH:mm"/></td>
                        <td class="text-center">${order.itemCount}</td>
                        <td class="text-end money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></td>
                        <td class="text-center">
                            <c:choose>
                                <c:when test="${order.status.name() == 'COMPLETED'}"><span class="badge bg-success badge-status">${order.status}</span></c:when>
                                <c:when test="${order.status.name() == 'CANCELLED'}"><span class="badge bg-secondary badge-status">${order.status}</span></c:when>
                                <c:when test="${order.status.name() == 'PROCESSING'}"><span class="badge bg-info badge-status">${order.status}</span></c:when>
                                <c:otherwise><span class="badge bg-warning text-dark badge-status">${order.status}</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td class="text-end">
                            <a href="${pageContext.request.contextPath}/admin/orders?id=${order.orderId}" class="btn btn-outline-primary btn-sm">Manage</a>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty orders}">
                    <tr><td colspan="7" class="text-center text-muted py-4">No orders yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Orders - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <h4 class="fw-bold mb-3">Orders</h4>

    <div class="card card-hover">
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr>
                    <th>Order #</th><th>Customer</th><th>Date</th>
                    <th class="text-center">Items</th>
                    <th class="text-end">Total</th>
                    <th class="text-center">Status</th>
                    <th class="text-end">Actions</th>
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
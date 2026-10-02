<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="My Orders - Apach_PC/STORE"/>
<%@ include file="../../layouts/header.jspf" %>
<div class="container py-4">
    <h4 class="fw-bold mb-3">My Orders</h4>

    <div class="row g-3">
        <div class="col-lg-3">
            <%@ include file="account-nav.jspf" %>
        </div>

        <div class="col-lg-9">
            <div class="card card-hover">
                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Order #</th><th>Date</th><th>Items</th><th class="text-end">Total</th><th class="text-center">Status</th><th></th></tr>
                        </thead>
                        <tbody>
                        <c:choose>
                            <c:when test="${empty orders}">
                                <tr>
                                    <td colspan="6">
                                        <div class="text-center py-4 empty-state">
                                            <span class="empty-icon"><i class="bi bi-receipt-cutoff"></i></span>
                                            <h6 class="fw-bold mb-1">No orders yet</h6>
                                            <p class="text-muted mb-3">When you place an order it will appear here.</p>
                                            <a href="${pageContext.request.contextPath}/products" class="btn btn-brand btn-sm">Browse products</a>
                                        </div>
                                    </td>
                                </tr>
                            </c:when>
                            <c:otherwise>
                                <c:forEach var="order" items="${orders}">
                                    <tr>
                                        <td>#${order.orderId}</td>
                                        <td><fmt:formatDate value="${order.orderDate}" pattern="dd MMM yyyy HH:mm"/></td>
                                        <td>${order.itemCount}</td>
                                        <td class="text-end money">$<fmt:formatNumber value="${order.totalAmount}" pattern="#,##0.00"/></td>
                                        <td class="text-center">
                                            <span data-order-status="${order.orderId}">
                                                <c:set var="_statusLabel" value="${order.status}"/>
                                                <%@ include file="../../components/status-badge.jspf" %>
                                            </span>
                                        </td>
                                        <td class="text-end">
                                            <a href="${pageContext.request.contextPath}/account/orders?id=${order.orderId}" class="btn btn-outline-primary btn-sm">Details</a>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../../layouts/footer.jspf" %>

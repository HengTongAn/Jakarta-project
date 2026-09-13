<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Inventory - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <h4 class="fw-bold mb-3">Inventory Management</h4>

    <div class="row g-3 mb-3">
        <div class="col-lg-6">
            <div class="card card-hover h-100">
                <div class="card-header bg-white fw-semibold">Low stock products (threshold: 5)</div>
                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Product</th><th class="text-end">Stock</th><th class="text-end">Value</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach var="p" items="${lowStock}">
                            <tr>
                                <td class="small"><c:out value="${p.name}"/></td>
                                <td class="text-end text-warning fw-semibold">${p.stockQuantity}</td>
                                <td class="text-end money small">$<fmt:formatNumber value="${p.price * p.stockQuantity}" pattern="#,##0.00"/></td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty lowStock}">
                            <tr><td colspan="3" class="text-center text-muted py-3">No low stock items.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="col-lg-6">
            <div class="card card-hover h-100">
                <div class="card-header bg-white fw-semibold">Out of stock</div>
                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Product</th><th class="text-end">Stock</th><th class="text-end">Actions</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach var="p" items="${outOfStock}">
                            <tr>
                                <td class="small"><c:out value="${p.name}"/></td>
                                <td class="text-end text-danger fw-semibold">0</td>
                                <td class="text-end"><a href="${pageContext.request.contextPath}/admin/products?edit=${p.productId}" class="btn btn-outline-primary btn-sm">Restock via edit</a></td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty outOfStock}">
                            <tr><td colspan="3" class="text-center text-muted py-3">All products are in stock.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>

    <div class="card card-hover mb-3">
        <div class="card-header bg-white fw-semibold">Adjust stock (all products)</div>
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr>
                    <th>Product</th>
                    <th class="text-center">Current stock</th>
                    <th class="text-center">Status</th>
                    <th class="text-end" style="width:260px">New stock</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="p" items="${products}">
                    <tr>
                        <td class="small"><c:out value="${p.name}"/></td>
                        <td class="text-center fw-semibold">${p.stockQuantity}</td>
                        <td class="text-center">
                            <c:choose>
                                <c:when test="${p.status.name() == 'DISCONTINUED'}"><span class="badge bg-dark badge-status">Discontinued</span></c:when>
                                <c:when test="${p.status.name() == 'OUT_OF_STOCK'}"><span class="badge bg-danger badge-status">Out of stock</span></c:when>
                                <c:when test="${p.status.name() == 'LOW_STOCK'}"><span class="badge bg-warning text-dark badge-status">Low stock</span></c:when>
                                <c:otherwise><span class="badge bg-success badge-status">In stock</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <form method="post" action="${pageContext.request.contextPath}/admin/inventory" class="d-flex justify-content-end gap-1">
                                <input type="hidden" name="productId" value="${p.productId}">
                                <input type="number" name="newQuantity" min="0" class="form-control form-control-sm" style="width:110px"
                                       value="${p.stockQuantity}">
                                <button type="submit" class="btn btn-brand btn-sm">Set</button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
    </div>

    <div class="card card-hover">
        <div class="card-header bg-white fw-semibold">Recent inventory activity</div>
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr><th>Date</th><th>Product</th><th class="text-end">Change</th><th class="text-center">Action</th></tr>
                </thead>
                <tbody>
                <c:forEach var="log" items="${logs}">
                    <tr>
                        <td class="small"><fmt:formatDate value="${log.createdAt}" pattern="dd MMM HH:mm"/></td>
                        <td class="small"><c:out value="${log.productName}"/></td>
                        <td class="text-end small">${log.oldQuantity} &rarr; <b>${log.newQuantity}</b></td>
                        <td class="text-center">
                            <c:choose>
                                <c:when test="${log.action == 'ORDER_CREATED'}"><span class="badge bg-info badge-status">Order</span></c:when>
                                <c:when test="${log.action == 'ORDER_CANCELLED'}"><span class="badge bg-secondary badge-status">Cancelled</span></c:when>
                                <c:otherwise><span class="badge bg-success badge-status">Adjust</span></c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty logs}">
                    <tr><td colspan="4" class="text-center text-muted py-4">No inventory activity yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
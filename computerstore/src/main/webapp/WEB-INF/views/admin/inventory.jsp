<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Inventory - Admin"/>
<%@ include file="../layouts/header.jspf" %>
<%@ include file="../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <h4 class="fw-bold mb-3">Inventory Management</h4>

    <div class="card card-hover mb-3">
        <div class="card-header bg-body d-flex flex-wrap justify-content-between align-items-center gap-2">
            <span class="fw-semibold"><i class="bi bi-box-arrow-in-down me-1" aria-hidden="true"></i> Receive stock</span>
            <span class="small text-muted">Delivery arrived? Type the units received &mdash; no math needed.</span>
        </div>
        <div class="card-body">
            <form method="post" action="${pageContext.request.contextPath}/admin/inventory" class="row g-2 align-items-end">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="reason" value="RECEIVED">
                <div class="col-md-6 col-lg-5">
                    <label for="receiveProduct" class="form-label small fw-semibold">Product</label>
                    <select id="receiveProduct" name="productId" class="form-select form-select-sm" required>
                        <c:forEach var="p" items="${products}">
                            <option value="${p.productId}"><c:out value="${p.name}"/> (${p.stockQuantity})</option>
                        </c:forEach>
                    </select>
                </div>
                <div class="col-md-3 col-lg-2">
                    <label for="receiveQty" class="form-label small fw-semibold">+ Units received</label>
                    <input id="receiveQty" name="delta" type="number" min="1" step="1" class="form-control form-control-sm" placeholder="20" required>
                </div>
                <div class="col-12 col-md-auto">
                    <button type="submit" class="btn btn-brand btn-sm w-100"><i class="bi bi-plus-lg me-1" aria-hidden="true"></i>Add to stock</button>
                </div>
            </form>
        </div>
    </div>

    <div class="row g-3 mb-3">
        <div class="col-lg-6">
            <div class="card card-hover h-100">
<div class="card-header bg-body fw-semibold">Low stock (threshold: ${lowStockThreshold}) &middot; suggested restock to ${reorderTarget} units</div>                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Product</th><th class="text-end">Stock</th><th class="text-end">Value</th><th class="text-end">Suggested order</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach var="p" items="${lowStock}">
                            <tr>
                                <td class="small"><c:out value="${p.name}"/></td>
                                <td class="text-end text-warning fw-semibold">${p.stockQuantity}</td>
                                <td class="text-end money small">$<fmt:formatNumber value="${p.price * p.stockQuantity}" pattern="#,##0.00"/></td>
                                <td class="text-end text-success fw-semibold">+${reorderTarget - p.stockQuantity}</td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty lowStock}">
                            <tr><td colspan="4" class="text-center text-muted py-3">No low stock items.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="col-lg-6">
            <div class="card card-hover h-100">
<div class="card-header bg-body fw-semibold">Out of stock</div>                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Product</th><th class="text-end">Stock</th><th class="text-end">Actions</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach var="p" items="${outOfStock}">
                            <tr>
                                <td class="small"><c:out value="${p.name}"/></td>
                                <td class="text-end text-danger fw-semibold">0</td>
                                <td>
                                    <div class="d-flex justify-content-end align-items-center gap-2">
                                        <span class="small text-muted">Suggested +${reorderTarget}</span>
                                        <form method="post" action="${pageContext.request.contextPath}/admin/inventory" class="d-flex gap-1">
                                            <input type="hidden" name="csrfToken" value="${csrfToken}">
                                            <input type="hidden" name="productId" value="${p.productId}">
                                            <input type="hidden" name="reason" value="RECEIVED">
                                            <input type="number" name="delta" min="1" step="1" class="form-control form-control-sm" style="width:90px" placeholder="+${reorderTarget}" aria-label="Units to restock">
                                            <button type="submit" class="btn btn-outline-primary btn-sm">Restock</button>
                                        </form>
                                    </div>
                                </td>
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
<div class="card-header bg-body d-flex flex-wrap justify-content-between align-items-center gap-2">            <span class="fw-semibold">Adjust stock (all products)</span>
            <span class="d-flex align-items-center gap-2">
                <span class="small text-muted">+ adds, &minus; removes &middot; every change is logged with a reason</span>
                <label for="stockFilter" class="visually-hidden">Filter products</label>
                <input type="search" id="stockFilter" class="form-control form-control-sm table-filter" placeholder="Filter products…">
            </span>
        </div>
        <div class="table-responsive">
            <table class="table align-middle mb-0" data-sortable data-filter-target="stockFilter">
                <thead class="table-light">
                <tr>
                    <th>Product</th>
                    <th class="text-center" data-sort="number">Current stock</th>
                    <th class="text-center">Status</th>
                    <th style="width:340px">Change &amp; reason</th>
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
                            <form method="post" action="${pageContext.request.contextPath}/admin/inventory" class="d-flex justify-content-end align-items-center gap-1">
                                <input type="hidden" name="csrfToken" value="${csrfToken}">
                                <input type="hidden" name="productId" value="${p.productId}">
                                <input type="number" name="delta" step="1" class="form-control form-control-sm" style="width:110px"
                                       placeholder="+20 or -3" aria-label="Change by">
                                <select name="reason" class="form-select form-select-sm" style="width:140px" aria-label="Reason">
                                    <option value="RECEIVED">Received</option>
                                    <option value="DAMAGED">Damaged</option>
                                    <option value="COUNT">Counted (stocktake)</option>
                                    <option value="RETURNED">Return</option>
                                    <option value="CORRECTION">Correction</option>
                                </select>
                                <button type="submit" class="btn btn-brand btn-sm">Apply</button>
                            </form>
                            <c:if test="${p.stockQuantity <= lowStockThreshold && p.status.name() != 'DISCONTINUED'}">
                                <div class="small text-muted text-end mt-1">Suggested +${reorderTarget - p.stockQuantity}
                                    <button type="button" class="btn btn-link btn-sm p-0 ms-1 align-baseline"
                                            onclick="var el = this.closest('td').querySelector('input[name=delta]'); el.value = '${reorderTarget - p.stockQuantity}';">use</button>
                                </div>
                            </c:if>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
    </div>

    <div class="card card-hover">
<div class="card-header bg-body d-flex justify-content-between align-items-center gap-2">            <span class="fw-semibold">Recent inventory activity</span>
            <span class="small text-muted">Last 15 changes</span>
        </div>
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
                                <c:when test="${log.action == 'MANUAL_ADJUST_RECEIVED'}"><span class="badge bg-success badge-status">Received</span></c:when>
                                <c:when test="${log.action == 'MANUAL_ADJUST_DAMAGED'}"><span class="badge bg-danger badge-status">Damaged</span></c:when>
                                <c:when test="${log.action == 'MANUAL_ADJUST_COUNT'}"><span class="badge bg-success badge-status">Counted</span></c:when>
                                <c:when test="${log.action == 'MANUAL_ADJUST_RETURNED'}"><span class="badge bg-secondary badge-status">Return</span></c:when>
                                <c:when test="${log.action == 'MANUAL_ADJUST_CORRECTION'}"><span class="badge bg-warning text-dark badge-status">Correction</span></c:when>
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
<%@ include file="../layouts/footer.jspf" %>
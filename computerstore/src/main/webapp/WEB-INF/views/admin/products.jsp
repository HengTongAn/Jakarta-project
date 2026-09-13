<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Products - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <h4 class="fw-bold mb-0">Manage Products</h4>
        <a href="${pageContext.request.contextPath}/admin/products?action=new" class="btn btn-brand">Add product</a>
    </div>

    <div class="card card-hover">
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr>
                    <th>Image</th>
                    <th>Product</th>
                    <th>Category</th>
                    <th>Brand</th>
                    <th>SKU</th>
                    <th class="text-end">Price</th>
                    <th class="text-center">Stock</th>
                    <th class="text-center">Status</th>
                    <th class="text-end">Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="p" items="${products}">
                    <tr>
                        <td>
                            <c:choose>
                                <c:when test="${p.hasImage()}">
                                    <img src="${pageContext.request.contextPath}/${p.imageUrl}" alt="${p.name}" class="img-thumbnail" style="width: 50px; height: 50px; object-fit: cover;">
                                </c:when>
                                <c:otherwise>
                                    <div class="bg-secondary d-flex align-items-center justify-content-center" style="width: 50px; height: 50px; border-radius: 4px;">
                                        <span class="text-white small">No img</span>
                                    </div>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td class="fw-semibold">
                            <a href="${pageContext.request.contextPath}/products?id=${p.productId}" class="text-decoration-none"><c:out value="${p.name}"/></a>
                        </td>
                        <td><c:out value="${p.categoryName}"/></td>
                        <td><c:out value="${p.brandName}"/></td>
                        <td class="small text-muted">${p.sku}</td>
                        <td class="text-end money">$<fmt:formatNumber value="${p.price}" pattern="#,##0.00"/></td>
                        <td class="text-center">${p.stockQuantity}</td>
                        <td class="text-center">
                            <c:choose>
                                <c:when test="${p.status.name() == 'DISCONTINUED'}"><span class="badge bg-dark badge-status">Discontinued</span></c:when>
                                <c:when test="${p.status.name() == 'OUT_OF_STOCK'}"><span class="badge bg-danger badge-status">Out of stock</span></c:when>
                                <c:when test="${p.status.name() == 'LOW_STOCK'}"><span class="badge bg-warning text-dark badge-status">Low stock</span></c:when>
                                <c:otherwise><span class="badge bg-success badge-status">In stock</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td class="text-end">
                            <a href="${pageContext.request.contextPath}/admin/products?edit=${p.productId}" class="btn btn-outline-primary btn-sm">Edit</a>
                            <a href="${pageContext.request.contextPath}/admin/products?delete=${p.productId}" class="btn btn-outline-danger btn-sm"
                               onclick="return confirm('Delete this product? Products with order history are disabled instead.');">Delete</a>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty products}">
                    <tr><td colspan="9" class="text-center text-muted py-4">No products found.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
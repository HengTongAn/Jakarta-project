<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${empty product ? 'Add' : 'Edit'} Product - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <h4 class="fw-bold mb-0"><c:out value="${empty product ? 'Add new product' : 'Edit product'}"/></h4>
        <a href="${pageContext.request.contextPath}/admin/products" class="btn btn-outline-secondary btn-sm">&larr; Back to products</a>
    </div>

    <c:if test="${not empty error}">
        <div class="alert alert-danger">${error}</div>
    </c:if>

    <div class="card card-hover">
        <div class="card-body p-4">
            <form method="post" action="${pageContext.request.contextPath}/admin/products" enctype="multipart/form-data">
                <c:if test="${not empty product}">
                    <input type="hidden" name="productId" value="${product.productId}">
                </c:if>

                <div class="row g-3">
                    <div class="col-md-6">
                        <label class="form-label">Product name *</label>
                        <input type="text" name="name" class="form-control" required
                               value="<c:out value="${empty product ? param.name : product.name}"/>">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">SKU *</label>
                        <input type="text" name="sku" class="form-control" required
                               value="<c:out value="${empty product ? param.sku : product.sku}"/>">
                        <div class="form-text">Must be unique.</div>
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">Category *</label>
                        <select name="categoryId" class="form-select" required>
                            <option value="">Select category</option>
                            <c:forEach var="cat" items="${categories}">
                                <option value="${cat.categoryId}"
                                        <c:if test="${selectedCategoryId == cat.categoryId}">selected</c:if>>
                                    <c:out value="${cat.name}"/>
                                </option>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">Brand *</label>
                        <select name="brandId" class="form-select" required>
                            <option value="">Select brand</option>
                            <c:forEach var="brand" items="${brands}">
                                <option value="${brand.brandId}"
                                        <c:if test="${selectedBrandId == brand.brandId}">selected</c:if>>
                                    <c:out value="${brand.name}"/>
                                </option>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="col-12">
                        <label class="form-label">Description</label>
                        <textarea name="description" class="form-control" rows="3"><c:out value="${empty product ? param.description : product.description}"/></textarea>
                    </div>
                    <div class="col-12">
                        <label class="form-label">Product image</label>
                        <div class="mb-2">
                            <c:if test="${not empty currentImageUrl}">
                                <div class="mb-2">
                                    <img src="${currentImageUrl}" alt="Current product image" class="img-thumbnail" style="max-height: 150px;">
                                    <div class="form-text">Current image</div>
                                </div>
                            </c:if>
                            <input type="file" name="productImage" class="form-control" accept="image/jpeg,image/png,image/gif,image/webp">
                            <div class="form-text">Leave empty to keep existing image. Max file size: 5MB. Allowed formats: JPG, PNG, GIF, WEBP.</div>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">Price ($) *</label>
                        <input type="number" step="0.01" min="0.01" name="price" class="form-control" required
                               value="<c:out value="${empty product ? param.price : product.price}"/>">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">Stock quantity</label>
                        <input type="number" step="1" min="0" name="stockQuantity" class="form-control"
                               value="<c:out value="${empty product ? (empty param.stockQuantity ? 0 : param.stockQuantity) : product.stockQuantity}"/>">
                    </div>
                </div>

                <div class="mt-4 d-flex gap-2">
                    <button type="submit" class="btn btn-brand">
                        <c:out value="${empty product ? 'Create product' : 'Save changes'}"/>
                    </button>
                    <a href="${pageContext.request.contextPath}/admin/products" class="btn btn-outline-secondary">Cancel</a>
                </div>
            </form>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
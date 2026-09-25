<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${empty product ? 'Add' : 'Edit'} Product - Admin"/>
<%@ include file="../../layouts/header.jspf" %>
<%@ include file="../../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <h4 class="fw-bold mb-0"><c:out value="${empty product ? 'Add new product' : 'Edit product'}"/></h4>
        <a href="${pageContext.request.contextPath}/admin/products" class="btn btn-outline-secondary btn-sm">&larr; Back to products</a>
    </div>

    <c:if test="${not empty error}">
        <div class="alert alert-danger"><c:out value="${error}"/></div>
    </c:if>

    <div class="card card-hover">
        <div class="card-body p-4">
            <form method="post" action="${pageContext.request.contextPath}/admin/products" enctype="multipart/form-data">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <c:if test="${not empty product}">
                    <input type="hidden" name="productId" value="${product.productId}">
                </c:if>

                <div class="row g-3">
                    <div class="col-6 col-md-6">
                        <label class="form-label">Product name *</label>
                        <input type="text" name="name" id="name" class="form-control" required
                               value="<c:out value="${empty product ? param.name : product.name}"/>">
                    </div>
                    <div class="col-6 col-md-6">
                        <label class="form-label">SKU *</label>
                        <input type="text" name="sku" class="form-control" required
                               value="<c:out value="${empty product ? param.sku : product.sku}"/>">
                        <div class="form-text">Must be unique.</div>
                    </div>
                    <div class="col-6 col-md-6">
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
                    <div class="col-6 col-md-6">
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
                        <textarea name="description" id="description" class="form-control" rows="3"><c:out value="${empty product ? param.description : product.description}"/></textarea>
                    </div>
                    <div class="col-12">
                        <label class="form-label">Product image</label>
                        <div class="mb-2 image-upload-field">
                            <div id="productImagePreview" class="product-image-preview mb-2">
                            <c:if test="${not empty currentImageUrl}">
                                <img src="${currentImageUrl}" alt="Current product image">
                                <span class="image-preview-label">Current image</span>
                            </c:if>
                            <c:if test="${empty currentImageUrl}"><span class="image-preview-empty"><i class="bi bi-image"></i> Image preview</span></c:if>
                            </div>
                            <input type="file" name="productImage" class="form-control" accept="image/jpeg,image/png,image/gif,image/webp" data-image-input data-preview-target="#productImagePreview" data-image-status="#productImageStatus">
                            <div id="productImageStatus" class="form-text" aria-live="polite">Leave empty to keep existing image. Max file size: 5MB. Allowed formats: JPG, PNG, GIF, WEBP.</div>
                        </div>
                    </div>
                    <div class="col-6 col-md-6">
                        <label class="form-label">Price ($) *</label>
                        <input type="number" step="0.01" min="0.01" name="price" class="form-control" required
                               value="<c:out value="${empty product ? param.price : product.price}"/>">
                    </div>
                    <div class="col-6 col-md-6">
                        <label class="form-label">Stock quantity</label>
                        <input type="number" step="1" min="0" name="stockQuantity" class="form-control"
                               value="<c:out value="${empty product ? (empty param.stockQuantity ? 0 : param.stockQuantity) : product.stockQuantity}"/>">
                    </div>
                </div>

                <hr class="my-4">
                <h6 class="fw-bold mb-3"><i class="bi bi-card-list me-1" aria-hidden="true"></i> Product details</h6>
                <div class="row g-3">
                    <div class="col-12">
                        <label class="form-label">Highlights</label>
                        <textarea name="highlights" id="highlightsField" class="form-control" rows="4"
                                  placeholder="One selling point per line, e.g.&#10;16-inch 165Hz QHD+ display&#10;Intel Core i9-14900HX processor"><c:out value="${empty product ? param.highlights : product.highlights}"/></textarea>
                        <div class="form-text">One highlight per line — rendered as bullets on the product page.</div>
                    </div>
                    <div class="col-6 col-md-6">
                        <label class="form-label">What's in the box</label>
                        <input type="text" name="boxContents" id="boxContentsField" class="form-control" maxlength="500"
                               value="<c:out value="${empty product ? param.boxContents : product.boxContents}"/>">
                    </div>
                    <div class="col-6 col-md-6">
                        <label class="form-label">Warranty</label>
                        <input type="text" name="warrantyInfo" id="warrantyInfoField" class="form-control" maxlength="255"
                               value="<c:out value="${empty product ? param.warrantyInfo : product.warrantyInfo}"/>">
                    </div>
                    <div class="col-12">
                        <label class="form-label">Official source URL</label>
                        <input type="url" name="sourceUrl" id="sourceUrlField" class="form-control" maxlength="500"
                               placeholder="https://www.manufacturer.example/product/specs"
                               value="<c:out value="${empty product ? param.sourceUrl : product.sourceUrl}"/>">
                        <div class="form-text">The official page these details came from — shown as a link on the product page.</div>
                    </div>

                    <div class="col-12">
                        <label class="form-label">Specifications</label>
                        <div class="table-responsive">
                            <table class="table table-sm align-middle" id="specEditor">
                                <thead>
                                    <tr>
                                        <th class="w-25">Name</th>
                                        <th>Value</th>
                                        <th style="width:64px"></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="spec" items="${product.specs}">
                                        <tr>
                                            <td><input type="text" name="specKey" class="form-control" maxlength="100" value="<c:out value='${spec.specKey}'/>"></td>
                                            <td><input type="text" name="specValue" class="form-control" maxlength="500" value="<c:out value='${spec.specValue}'/>"></td>
                                            <td><button type="button" class="btn btn-sm btn-outline-danger" data-remove-spec aria-label="Remove this specification">&#10005;</button></td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </div>
                        <button type="button" class="btn btn-sm btn-outline-secondary" id="addSpecBtn">+ Add specification</button>
                        <div class="form-text">e.g. Display: 15.6&quot; QHD+ 165Hz. Add key/value pairs as needed.</div>
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
<script src="${pageContext.request.contextPath}/assets/js/product-spec-editor.js"></script>
<%@ include file="../../layouts/footer.jspf" %>

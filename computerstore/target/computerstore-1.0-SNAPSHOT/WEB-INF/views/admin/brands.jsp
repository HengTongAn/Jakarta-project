<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Brands - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <h4 class="fw-bold mb-0"><c:out value="${empty editing ? 'Manage Brands' : 'Edit Brand'}"/></h4>
        <c:if test="${not empty editing}">
            <a href="${pageContext.request.contextPath}/admin/brands" class="btn btn-outline-secondary btn-sm">Cancel edit</a>
        </c:if>
    </div>

    <c:if test="${not empty error}">
        <div class="alert alert-danger">${error}</div>
    </c:if>

    <div class="row g-3">
        <div class="col-lg-4">
            <div class="card card-hover">
                <div class="card-body">
                    <h6 class="fw-bold mb-3">${empty editing ? 'New brand' : 'Update brand'}</h6>
                    <form method="post" action="${pageContext.request.contextPath}/admin/brands">
                        <c:if test="${not empty editing}">
                            <input type="hidden" name="brandId" value="${editing.brandId}">
                        </c:if>
                        <div class="mb-3">
                            <label class="form-label">Name *</label>
                            <input type="text" name="name" class="form-control" required
                                   value="<c:out value="${empty editing ? param.name : editing.name}"/>">
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Description</label>
                            <textarea name="description" class="form-control" rows="2"><c:out value="${empty editing ? param.description : editing.description}"/></textarea>
                        </div>
                        <button type="submit" class="btn btn-brand w-100">${empty editing ? 'Create brand' : 'Save changes'}</button>
                    </form>
                </div>
            </div>
        </div>
        <div class="col-lg-8">
            <div class="card card-hover">
                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead class="table-light">
                        <tr><th>Name</th><th>Description</th><th class="text-center">Products</th><th class="text-end">Actions</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach var="brand" items="${brands}">
                            <tr>
                                <td class="fw-semibold"><c:out value="${brand.name}"/></td>
                                <td class="small text-muted"><c:out value="${brand.description}"/></td>
                                <td class="text-center">${brand.productCount}</td>
                                <td class="text-end">
                                    <a href="${pageContext.request.contextPath}/admin/brands?edit=${brand.brandId}" class="btn btn-outline-primary btn-sm">Edit</a>
                                    <a href="${pageContext.request.contextPath}/admin/brands?delete=${brand.brandId}" class="btn btn-outline-danger btn-sm"
                                       onclick="return confirm('Delete this brand?');">Delete</a>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty brands}">
                            <tr><td colspan="4" class="text-center text-muted py-4">No brands yet.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
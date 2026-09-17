<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="My Profile - TechStore"/>
<%@ include file="../common/header.jspf" %>
<div class="container py-4">
    <h4 class="fw-bold mb-3">My Profile</h4>

    <div class="row g-3">
        <div class="col-lg-3">
            <div class="card card-hover customer-sidebar">
                <div class="card-body p-0">
                    <div class="list-group list-group-flush">
                        <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account">Dashboard</a>
                        <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account/orders">My Orders</a>
                        <a class="list-group-item list-group-item-action active" href="${pageContext.request.contextPath}/account/profile">Profile</a>
                        <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account/settings"><i class="bi bi-shield-lock me-2"></i>Security</a>
                    </div>
                </div>
            </div>
        </div>

        <div class="col-lg-9">
            <div class="card card-hover">
                <div class="card-body p-4">

                    <div class="d-flex align-items-center gap-4 mb-4 pb-3 border-bottom profile-avatar-row">
                        <div id="profileAvatarPreview" class="avatar-preview image-upload-preview">
                            <c:choose>
                                <c:when test="${not empty sessionScope.user.avatarUrl}">
                                    <img src="${pageContext.request.contextPath}/${sessionScope.user.avatarUrl}" alt="avatar">
                                </c:when>
                                <c:otherwise>
                                    <i class="bi bi-person-circle"></i>
                                </c:otherwise>
                            </c:choose>
                        </div>
                        <form method="post" action="${pageContext.request.contextPath}/account/avatar" class="profile-upload-form"
                              enctype="multipart/form-data">
                            <input type="hidden" name="csrfToken" value="${csrfToken}">
                            <label class="form-label fw-bold mb-1">Profile picture</label>
                            <input type="file" name="avatarFile" class="form-control form-control-sm mb-2"
                                   accept="image/png,image/jpeg,image/gif,image/webp" data-image-input data-preview-target="#profileAvatarPreview" data-image-status="#profileAvatarStatus">
                            <button type="submit" class="btn btn-sm btn-outline-brand" data-loading="Uploading…">Upload</button>
                            <div id="profileAvatarStatus" class="form-text" aria-live="polite">JPG, PNG, GIF or WEBP. Max 5 MB.</div>
                        </form>
                    </div>

                    <form method="post" action="${pageContext.request.contextPath}/account/profile">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <div class="mb-3">
                            <label class="form-label">Username</label>
                            <input type="text" class="form-control" value="${sessionScope.user.username}" readonly>
                        </div>
                        <div class="row">
                            <div class="col-md-6 mb-3">
                                <label class="form-label">Full name</label>
                                <input type="text" name="fullName" class="form-control" required
                                       value="<c:out value='${sessionScope.user.fullName}'/>">
                            </div>
                            <div class="col-md-6 mb-3">
                                <label class="form-label">Email</label>
                                <input type="email" name="email" class="form-control" required
                                       value="<c:out value='${sessionScope.user.email}'/>">
                            </div>
                        </div>
                        <button type="submit" class="btn btn-brand profile-save-button">Save changes</button>
                    </form>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>

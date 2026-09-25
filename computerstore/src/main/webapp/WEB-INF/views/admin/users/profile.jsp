<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="${profileUser.fullName} - User Profile"/>
<%@ include file="../../layouts/header.jspf" %>
<%@ include file="../../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <nav class="mb-3" aria-label="Breadcrumb">
        <ol class="breadcrumb small mb-0">
            <li class="breadcrumb-item"><a href="${pageContext.request.contextPath}/admin">Dashboard</a></li>
            <li class="breadcrumb-item"><a href="${pageContext.request.contextPath}/admin/users">Users</a></li>
            <li class="breadcrumb-item active" aria-current="page"><c:out value="${profileUser.username}"/></li>
        </ol>
    </nav>

    <div class="row g-3">
        <div class="col-lg-4">
            <div class="card card-hover h-100">
                <div class="card-body text-center">
                    <c:choose>
                        <c:when test="${not empty profileUser.avatarUrl}">
                            <img src="${pageContext.request.contextPath}/${profileUser.avatarUrl}" alt="Profile picture" class="rounded-circle object-fit-cover mb-3"
                                 style="width: 96px; height: 96px;">
                        </c:when>
                        <c:otherwise>
                            <div class="profile-avatar fs-1 fw-bold rounded-circle mx-auto mb-3">
                                ${fn:substring(profileUser.fullName, 0, 1)}
                            </div>
                        </c:otherwise>
                    </c:choose>
                    <h5 class="fw-bold mb-1"><c:out value="${profileUser.fullName}"/></h5>
                    <div class="text-muted small mb-2">@<c:out value="${profileUser.username}"/></div>
                    <div class="d-flex justify-content-center gap-2 mb-3">
                        <span class="badge ${profileUser.role.name() == 'SUPER_ADMIN' ? 'bg-dark' : profileUser.role.name() == 'ADMIN' ? 'bg-danger' : 'bg-secondary'}">${profileUser.role.name()}</span>
                        <span class="presence-status ${profileUser.isActiveRecently(onlineThreshold) ? 'live' : 'offline'}">
                            <span class="presence-dot" aria-hidden="true"></span>
                            ${profileUser.isActiveRecently(onlineThreshold) ? 'Live' : 'Not live'}
                        </span>
                    </div>
                    <div class="d-grid gap-2">
                        <a class="btn btn-brand btn-sm"
                           href="${pageContext.request.contextPath}/admin/users?action=edit&id=${profileUser.userId}">
                            <i class="bi bi-pencil-square me-1"></i>Edit profile
                        </a>
                        <a class="btn btn-outline-secondary btn-sm"
                           href="${pageContext.request.contextPath}/admin/users?action=reset&id=${profileUser.userId}">
                            <i class="bi bi-key me-1"></i>Reset password
                        </a>
                        <a class="btn btn-outline-secondary btn-sm"
                           href="${pageContext.request.contextPath}/admin/users">
                            <i class="bi bi-arrow-left me-1"></i>Back to users
                        </a>
                    </div>
                </div>
            </div>
        </div>

        <div class="col-lg-8">
            <div class="card card-hover">
<div class="card-header bg-body fw-semibold"><i class="bi bi-person-lines-fill me-1 text-brand"></i>Account details</div>                <div class="card-body">
                    <dl class="profile-dl row mb-0">
                        <dt class="col-sm-4 text-muted">User ID</dt>
                        <dd class="col-sm-8">${profileUser.userId}</dd>
                        <dt class="col-sm-4 text-muted">Username</dt>
                        <dd class="col-sm-8"><c:out value="${profileUser.username}"/></dd>
                        <dt class="col-sm-4 text-muted">Full name</dt>
                        <dd class="col-sm-8"><c:out value="${profileUser.fullName}"/></dd>
                        <dt class="col-sm-4 text-muted">Email</dt>
                        <dd class="col-sm-8"><a href="mailto:${profileUser.email}"><c:out value="${profileUser.email}"/></a></dd>
                        <dt class="col-sm-4 text-muted">Role</dt>
                        <dd class="col-sm-8">
<span class="badge ${profileUser.role.name() == 'SUPER_ADMIN' ? 'bg-dark' : profileUser.role.name() == 'ADMIN' ? 'bg-danger' : 'bg-secondary'}">${profileUser.role.name()}</span>
                        </dd>
                        <dt class="col-sm-4 text-muted">Joined</dt>
                        <dd class="col-sm-8"><fmt:formatDate value="${profileUser.createdAt}" pattern="EEEE, dd MMMM yyyy 'at' HH:mm"/></dd>
                        <dt class="col-sm-4 text-muted">Last active</dt>
                        <dd class="col-sm-8">
                            <c:choose>
                                <c:when test="${not empty profileUser.lastActiveAt}">
                                    <fmt:formatDate value="${profileUser.lastActiveAt}" pattern="dd MMM yyyy, HH:mm"/>
                                </c:when>
                                <c:otherwise>
                                    <span class="text-muted">Never</span>
                                </c:otherwise>
                            </c:choose>
                        </dd>
                    </dl>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../../layouts/footer.jspf" %>

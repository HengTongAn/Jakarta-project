<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Users - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h4 class="fw-bold mb-0">Users <span class="badge bg-secondary rounded-pill align-middle" id="usersCount">${users.size()}</span></h4>
        <div class="d-flex gap-2">
            <label for="usersFilter" class="visually-hidden">Filter users</label>
            <input type="search" id="usersFilter" class="form-control form-control-sm table-filter" placeholder="Filter users…">
            <a href="${pageContext.request.contextPath}/admin/users?action=new" class="btn btn-brand btn-sm">+ Add User</a>
        </div>
    </div>

    <div class="card card-hover">
        <div class="table-responsive">
            <table class="table align-middle mb-0" data-sortable data-filter-target="usersFilter" data-count="usersCount">
                <thead class="table-light">
                <tr><th data-sort="number">#</th><th>Username</th><th>Full name</th><th>Email</th><th>Role</th><th>Status</th><th>Joined</th><th class="text-end" data-nosort>Actions</th></tr>
                </thead>
                <tbody>
                <c:forEach var="u" items="${users}">
                    <tr>
                        <td>${u.userId}</td>
                        <td class="fw-semibold">
                            <a class="link-body fw-semibold user-profile-link"
                               href="${pageContext.request.contextPath}/admin/users?action=profile&id=${u.userId}">
                                <c:out value="${u.username}"/>
                            </a>
                            <c:if test="${sessionScope.user.userId == u.userId}">
                                <span class="badge bg-info text-dark ms-1">you</span>
                            </c:if>
                        </td>
                        <td><c:out value="${u.fullName}"/></td>
                        <td><c:out value="${u.email}"/></td>
                        <td>
                            <span class="badge ${u.role.name() == 'ADMIN' ? 'bg-danger' : 'bg-secondary'}">
                                ${u.role.name()}
                            </span>
                        </td>
                        <td>
                            <span class="presence-status ${u.isActiveRecently(onlineThreshold) ? 'live' : 'offline'}">
                                <span class="presence-dot" aria-hidden="true"></span>
                                ${u.isActiveRecently(onlineThreshold) ? 'Live' : 'Not live'}
                            </span>
                        </td>
                        <td class="small"><fmt:formatDate value="${u.createdAt}" pattern="dd MMM yyyy"/></td>
                        <td class="text-end text-nowrap">
                            <a class="btn btn-sm btn-outline-primary"
                               href="${pageContext.request.contextPath}/admin/users?action=profile&id=${u.userId}">Profile</a>
                            <a class="btn btn-sm btn-outline-secondary"
                               href="${pageContext.request.contextPath}/admin/users?action=edit&id=${u.userId}">Edit</a>
                            <a class="btn btn-sm btn-outline-secondary"
                               href="${pageContext.request.contextPath}/admin/users?action=reset&id=${u.userId}">Reset Password</a>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty users}">
                    <tr><td colspan="8" class="text-center text-muted py-4">No users yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>

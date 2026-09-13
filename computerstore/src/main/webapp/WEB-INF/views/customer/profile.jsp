<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="My Profile - TechStore"/>
<%@ include file="../common/header.jspf" %>
<div class="container py-4">
    <h4 class="fw-bold mb-3">My Profile</h4>

    <div class="row g-3">
        <div class="col-lg-3">
            <div class="card card-hover">
                <div class="card-body p-0">
                    <div class="list-group list-group-flush">
                        <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account">Dashboard</a>
                        <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account/orders">My Orders</a>
                        <a class="list-group-item list-group-item-action active" href="${pageContext.request.contextPath}/account/profile">Profile</a>
                    </div>
                </div>
            </div>
        </div>

        <div class="col-lg-9">
            <div class="card card-hover">
                <div class="card-body p-4">
                    <form method="post" action="${pageContext.request.contextPath}/account/profile">
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
                        <hr>
                        <h6 class="fw-bold mb-3">Change password (optional)</h6>
                        <div class="row">
                            <div class="col-md-4 mb-3">
                                <label class="form-label">Current password</label>
                                <input type="password" name="currentPassword" class="form-control" autocomplete="current-password">
                            </div>
                            <div class="col-md-4 mb-3">
                                <label class="form-label">New password</label>
                                <input type="password" name="newPassword" class="form-control" autocomplete="new-password">
                                <div class="form-text">Minimum 6 characters. Leave blank to keep current password.</div>
                            </div>
                        </div>
                        <button type="submit" class="btn btn-brand">Save changes</button>
                    </form>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
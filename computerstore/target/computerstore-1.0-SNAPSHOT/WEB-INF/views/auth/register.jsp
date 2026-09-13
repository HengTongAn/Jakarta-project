<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Create Account - TechStore"/>
<%@ include file="../common/header.jspf" %>
<div class="container my-5">
    <div class="row justify-content-center">
        <div class="col-md-6 col-lg-5">
            <div class="card card-hover">
                <div class="card-body p-4">
                    <h4 class="card-title mb-1 fw-bold">Create your account</h4>
                    <p class="text-muted small mb-4">Join to browse, order and track your purchase history.</p>

                    <c:if test="${not empty error}">
                        <div class="alert alert-danger py-2 small">${error}</div>
                    </c:if>

                    <form method="post" action="${pageContext.request.contextPath}/register">
                        <div class="mb-3">
                            <label class="form-label">Username</label>
                            <input type="text" name="username" class="form-control" required
                                   value="<c:out value='${username}'/>">
                            <div class="form-text">3-30 characters, letters / digits / underscore.</div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Full name</label>
                            <input type="text" name="fullName" class="form-control" required
                                   value="<c:out value='${fullName}'/>">
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Email</label>
                            <input type="email" name="email" class="form-control" required
                                   value="<c:out value='${email}'/>">
                        </div>
                        <div class="row">
                            <div class="col-md-6 mb-3">
                                <label class="form-label">Password</label>
                                <input type="password" name="password" class="form-control" required>
                            </div>
                            <div class="col-md-6 mb-3">
                                <label class="form-label">Confirm password</label>
                                <input type="password" name="confirmPassword" class="form-control" required>
                            </div>
                        </div>
                        <button type="submit" class="btn btn-brand w-100">Create account</button>
                    </form>

                    <p class="text-center mt-3 mb-0 small">
                        Already have an account?
                        <a href="${pageContext.request.contextPath}/login">Login</a>
                    </p>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
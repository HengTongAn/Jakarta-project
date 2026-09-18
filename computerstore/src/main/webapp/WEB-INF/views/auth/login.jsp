<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Login - TechStore"/>
<%@ include file="../common/header.jspf" %>
<div class="container my-5">
    <div class="row justify-content-center">
        <div class="col-md-5 col-lg-4">
            <div class="text-center mb-4">
                <img class="auth-logo" src="${pageContext.request.contextPath}/assets/images/techstore-mark.svg" alt="TechStore" style="height:44px;width:auto">
            </div>
            <div class="card card-hover">
                <div class="card-body p-4">
                    <p class="text-muted small mb-4">Sign in to continue shopping or manage the store.</p>

                    <c:if test="${not empty info}">
			<div class="alert alert-info py-2 small"><c:out value="${info}"/></div>
                    </c:if>
                    <c:if test="${not empty error}">
                        <div class="alert alert-danger py-2 small"><c:out value="${error}"/></div>
                    </c:if>

                    <form method="post" action="${pageContext.request.contextPath}/login">
	                   <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="return" value="<c:out value="${param['return']}"/>">
                        <div class="mb-3">
                            <label class="form-label">Username</label>
                            <input type="text" name="username" class="form-control" required
                                   value="<c:out value='${username}'/>">
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Password</label>
                            <div class="password-field">
                                <input type="password" name="password" id="loginPassword" class="form-control" required
                                       autocomplete="current-password">
                                <button type="button" class="password-toggle" data-target="loginPassword" aria-label="Show password" tabindex="-1">
                                    <i class="bi bi-eye"></i>
                                </button>
                            </div>
                        </div>
                        <button type="submit" class="btn btn-brand w-100">Login</button>
                    </form>

                    <p class="text-center mt-3 mb-0 small">
                        Don't have an account?
                        <a href="${pageContext.request.contextPath}/register">Register</a>
                    </p>

                    <hr>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>

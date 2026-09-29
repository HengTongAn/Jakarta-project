<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Forgot Password - Apach_PC/STORE"/>
<%@ include file="../layouts/header.jspf" %>
<div class="container my-5">
    <div class="row justify-content-center">
        <div class="col-md-5 col-lg-4">
            <div class="text-center mb-4">
                <img class="auth-logo" src="${pageContext.request.contextPath}/assets/images/apach-pc-store.svg" alt="Apach_PC/STORE" style="height:44px;width:auto">
                <h4 class="fw-bold mb-0 mt-3">Apach_PC/STORE</h4>
            </div>
            <div class="card card-hover">
                <div class="card-body p-4">
                    <h4 class="card-title mb-1 fw-bold">Forgot your password?</h4>
                    <p class="text-muted small mb-4">Enter the email you registered with and we'll send you a link to choose a new password.</p>

                    <c:if test="${not empty info}">
                        <div class="alert alert-info py-2 small"><c:out value="${info}"/></div>
                    </c:if>
                    <c:if test="${not empty error}">
                        <div class="alert alert-danger py-2 small"><c:out value="${error}"/></div>
                    </c:if>

                    <c:choose>
                        <c:when test="${not empty info}">
                            <p class="text-center mt-3 mb-0 small">
                                Return to
                                <a href="${pageContext.request.contextPath}/login">Login</a>
                            </p>
                        </c:when>
                        <c:otherwise>
                            <form method="post" action="${pageContext.request.contextPath}/forgot">
                                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
                                <div class="mb-3">
                                    <label class="form-label">Email</label>
                                    <input type="email" name="email" class="form-control" required autofocus
                                           value="<c:out value='${email}'/>">
                                </div>
                                <button type="submit" class="btn btn-brand w-100">Send reset link</button>
                            </form>

                            <p class="text-center mt-3 mb-0 small">
                                Remembered it?
                                <a href="${pageContext.request.contextPath}/login">Login</a>
                            </p>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../layouts/footer.jspf" %>

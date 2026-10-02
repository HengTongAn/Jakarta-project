<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Verify Code - Apach_PC/STORE"/>
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
                    <h4 class="card-title mb-1 fw-bold">Get your code</h4>
                    <p class="text-muted small mb-4">Enter the 6-digit code sent to <strong><c:out value="${email}"/></strong></p>

                    <c:if test="${not empty error}">
                        <div class="alert alert-danger py-2 small"><c:out value="${error}"/></div>
                    </c:if>
                    <c:if test="${not empty info}">
                        <div class="alert alert-info py-2 small"><c:out value="${info}"/></div>
                    </c:if>

                    <form method="post" action="${pageContext.request.contextPath}/verify-code">
                        <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
                        <input type="hidden" name="email" value="<c:out value='${email}'/>">
                        <div class="mb-3">
                            <label class="form-label">Enter 6-digit code</label>
                            <input type="text" name="code" class="form-control text-center" maxlength="6" pattern="[0-9]{6}" 
                                   placeholder="000000" required autofocus autocomplete="one-time-code">
                            <div class="form-text">The code is valid for 10 minutes.</div>
                        </div>
                        <button type="submit" class="btn btn-brand w-100">Verify code</button>
                    </form>

                    <div class="text-center mt-3">
                        <p class="small text-muted mb-2">Didn't receive the code?</p>
                        <form method="post" action="${pageContext.request.contextPath}/resend-code">
                            <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
                            <input type="hidden" name="email" value="<c:out value='${email}'/>">
                            <button type="submit" class="btn btn-link btn-sm">Resend code</button>
                        </form>
                    </div>

                    <p class="text-center mt-3 mb-0 small">
                        Wrong email?
                        <a href="${pageContext.request.contextPath}/forgot">Try again</a>
                    </p>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../layouts/footer.jspf" %>

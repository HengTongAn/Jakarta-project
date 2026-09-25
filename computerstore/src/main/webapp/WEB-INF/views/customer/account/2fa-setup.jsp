<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Two-factor authentication - TechStore"/>
<%@ include file="../../layouts/header.jspf" %>
<div class="container py-4">
    <div class="row justify-content-center"><div class="col-lg-7">
        <div class="card card-hover"><div class="card-body p-4">
            <h4 class="fw-bold">Two-factor authentication</h4>
            <c:choose>
                <c:when test="${twoFactorEnabled}">
                    <p class="text-success">Two-factor authentication is enabled.</p>
                    <p class="text-muted small">Enter a current code from your authenticator app to disable it.</p>
                    <form method="post" action="${pageContext.request.contextPath}/account/2fa">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="disable">
                        <div class="mb-3"><label class="form-label" for="disableCode">Authentication code</label>
                            <input class="form-control" id="disableCode" name="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required></div>
                        <button class="btn btn-outline-danger" type="submit">Disable 2FA</button>
                    </form>
                </c:when>
                <c:when test="${not empty secret}">
                    <p>Save this secret in an authenticator app, then enter the generated 6-digit code to confirm setup.</p>
                    <div class="alert alert-warning"><strong>Secret:</strong> <code><c:out value="${secret}"/></code></div>
                    <p class="small text-muted">Manual setup URI: <code><c:out value="${qrCodeUrl}"/></code></p>
                    <form method="post" action="${pageContext.request.contextPath}/account/2fa">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="enable">
                        <div class="mb-3"><label class="form-label" for="enableCode">Authentication code</label>
                            <input class="form-control" id="enableCode" name="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required></div>
                        <button class="btn btn-brand" type="submit">Enable 2FA</button>
                    </form>
                </c:when>
                <c:otherwise>
                    <p class="text-muted">Add a second sign-in check using any TOTP-compatible authenticator app.</p>
                    <form method="post" action="${pageContext.request.contextPath}/account/2fa">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="generate">
                        <button class="btn btn-brand" type="submit">Set up 2FA</button>
                    </form>
                </c:otherwise>
            </c:choose>
            <p class="mt-4 mb-0"><a href="${pageContext.request.contextPath}/account/settings">Back to security settings</a></p>
        </div></div>
    </div></div>
</div>
<%@ include file="../../layouts/footer.jspf" %>

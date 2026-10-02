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
                    <p class="text-muted small mb-4">Enter the email you registered with and we'll email you a 6-digit code to verify it's really you.</p>

                    <c:if test="${not empty info}">
                        <div class="alert alert-info py-2 small"><c:out value="${info}"/></div>
                    </c:if>
                    <c:if test="${not empty error}">
                        <div class="alert alert-danger py-2 small"><c:out value="${error}"/></div>
                    </c:if>
                    <%-- Shown only when SMTP is genuinely unset, which is an operator
                         problem rather than a customer's. Without it the page promises a
                         code that can never arrive and the customer waits on an inbox
                         that will stay empty. Deliberately says nothing about whether
                         the address is registered: that stays uniform either way, so
                         this cannot be used to probe for accounts. --%>
                    <c:if test="${mailConfigured eq false}">
                        <div class="alert alert-warning py-2 small" role="alert">
                            <i class="bi bi-exclamation-triangle-fill me-1" aria-hidden="true"></i>
                            Email is not configured on this server, so no code or link can be sent
                            yet. An administrator needs to set MAIL_FROM, MAIL_USERNAME and
                            MAIL_PASSWORD before password recovery works.
                        </div>
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
                                           autocomplete="email"
                                           value="<c:out value='${email}'/>">
                                </div>
                                <button type="submit" class="btn btn-brand w-100">Email me a code</button>
                                <%-- The link flow is a second submit in the same form,
                                     not a second form: one form means one CSRF token
                                     and no way for the two paths to disagree about
                                     which address was typed. The controller reads
                                     'method', defaulting to the code flow, so a form
                                     that lost this button still sends a code. --%>
                                <button type="submit" name="method" value="link"
                                        class="btn btn-link w-100 mt-2 small">
                                    Email me a reset link instead
                                </button>
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

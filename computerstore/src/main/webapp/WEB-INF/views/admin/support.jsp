<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Contact Support - Admin"/>
<%@ include file="../layouts/header.jspf" %>
<%@ include file="../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
            <h4 class="fw-bold mb-1">Contact support channels</h4>
            <p class="text-muted small mb-0">These links appear in the site footer. Save and they are live immediately &mdash; no restart, no rebuild.</p>
        </div>
    </div>

    <c:if test="${not empty errors}">
        <div class="alert alert-danger">
            <i class="bi bi-exclamation-triangle me-1" aria-hidden="true"></i>
            Nothing was saved. Fix the highlighted field(s) below and submit again.
        </div>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/admin/support" novalidate>
        <input type="hidden" name="csrfToken" value="${csrfToken}">

        <div class="row g-3">
            <div class="col-lg-7">
                <div class="card card-hover">
                    <div class="card-body">
                        <h6 class="fw-bold mb-3">Destinations</h6>

                        <c:forEach var="ch" items="${channels}">
                            <div class="mb-3">
                                <label class="form-label d-flex align-items-center gap-2" for="support-${ch.key}">
                                    <img src="${pageContext.request.contextPath}/assets/images/${ch.icon}" alt="" width="18" height="18" loading="lazy" decoding="async">
                                    <c:out value="${ch.label}"/>
                                </label>
                                <c:set var="fieldError" value="${errors[ch.key]}"/>
                                <input type="url" id="support-${ch.key}" name="${ch.key}"
                                       class="form-control${empty fieldError ? '' : ' is-invalid'}"
                                       value="<c:out value='${urls[ch.key]}'/>"
                                       placeholder="<c:out value='${ch.example}'/>"
                                       inputmode="url" spellcheck="false" autocomplete="off">
                                <c:choose>
                                    <c:when test="${not empty fieldError}">
                                        <div class="invalid-feedback"><c:out value="${fieldError}"/></div>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="form-text">Leave blank to hide this channel from the footer.</div>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </c:forEach>

                        <button type="submit" class="btn btn-brand w-100">
                            <i class="bi bi-check2 me-1" aria-hidden="true"></i> Save channels
                        </button>
                    </div>
                </div>
            </div>

            <div class="col-lg-5">
                <div class="card card-hover">
                    <div class="card-body">
                        <h6 class="fw-bold mb-3">Preview</h6>
                        <p class="text-muted small">Exactly what visitors see in the footer, and where each link points.</p>

                        <ul class="support-channels mb-0">
                            <c:forEach var="ch" items="${channels}">
                                <li>
                                    <c:choose>
                                        <c:when test="${not empty urls[ch.key]}">
                                            <a class="support-chan" href="<c:out value='${urls[ch.key]}'/>" target="_blank" rel="noopener noreferrer">
                                                <img src="${pageContext.request.contextPath}/assets/images/${ch.icon}" alt="" width="22" height="22" loading="lazy" decoding="async"><span><c:out value="${ch.label}"/></span>
                                            </a>
                                        </c:when>
                                        <c:otherwise>
                                            <span class="support-chan is-unconfigured" aria-disabled="true">
                                                <img src="${pageContext.request.contextPath}/assets/images/${ch.icon}" alt="" width="22" height="22" loading="lazy" decoding="async"><span><c:out value="${ch.label}"/></span>
                                            </span>
                                        </c:otherwise>
                                    </c:choose>
                                </li>
                            </c:forEach>
                        </ul>

                        <hr>
                        <p class="text-muted small mb-0">
                            Only <code>http://</code> and <code>https://</code> destinations are accepted &mdash; anything else is rejected rather than rendered,
                            so a bad value can never turn the footer into a script injection point.
                        </p>
                    </div>
                </div>
            </div>
        </div>
    </form>
</div>
<%@ include file="../layouts/footer.jspf" %>

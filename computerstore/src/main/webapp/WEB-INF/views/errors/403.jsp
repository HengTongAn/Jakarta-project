<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Error 403 - Access Denied"/>
<%@ include file="../layouts/header.jspf" %>
<c:set var="_deniedByStorefrontGate" value="${not empty requestScope.deniedFromAdminPanel}"/>
<div class="container text-center py-5">
    <h1 class="display-1 fw-bold text-danger">403</h1>
    <h4>Access Denied</h4>
    <%-- Two different 403s land here: AdminAuthorizationFilter (not an admin) and
         StorefrontAccessFilter (an admin on the customer storefront), told apart by
         the request attribute the latter sets. The way back has to differ too --
         "Back to store" sent to an admin would land on another 403, which reads as
         a broken site rather than a deliberate rule. --%>
    <c:choose>
        <c:when test="${_deniedByStorefrontGate}">
            <p class="text-muted">The store is for customer accounts. Staff manage the shop from the admin panel.</p>
            <a href="${pageContext.request.contextPath}/admin" class="btn btn-brand"><i class="bi bi-grid" aria-hidden="true"></i> Go to admin panel</a>
        </c:when>
        <c:otherwise>
            <p class="text-muted">You do not have permission to view this page.</p>
            <a href="${pageContext.request.contextPath}/products" class="btn btn-brand">Back to store</a>
        </c:otherwise>
    </c:choose>
</div>
<%@ include file="../layouts/footer.jspf" %>

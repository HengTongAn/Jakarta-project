<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Error 404 - Not Found"/>
<%@ include file="../common/header.jspf" %>
<div class="container text-center py-5">
    <h1 class="display-1 fw-bold text-secondary">404</h1>
    <h4>Page Not Found</h4>
    <p class="text-muted">The page or product you are looking for does not exist.</p>
    <a href="${pageContext.request.contextPath}/products" class="btn btn-brand">Back to store</a>
</div>
<%@ include file="../common/footer.jspf" %>
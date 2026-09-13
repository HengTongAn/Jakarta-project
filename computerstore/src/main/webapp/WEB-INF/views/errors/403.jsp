<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Error 403 - Access Denied"/>
<%@ include file="../common/header.jspf" %>
<div class="container text-center py-5">
    <h1 class="display-1 fw-bold text-danger">403</h1>
    <h4>Access Denied</h4>
    <p class="text-muted">You do not have permission to view this page.</p>
    <a href="${pageContext.request.contextPath}/products" class="btn btn-brand">Back to store</a>
</div>
<%@ include file="../common/footer.jspf" %>
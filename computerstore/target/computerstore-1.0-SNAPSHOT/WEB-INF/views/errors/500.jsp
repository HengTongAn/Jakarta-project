<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Error 500 - Server Error"/>
<%@ include file="../common/header.jspf" %>
<div class="container text-center py-5">
    <h1 class="display-1 fw-bold text-danger">500</h1>
    <h4>Something went wrong</h4>
    <c:choose>
        <c:when test="${not empty errorMessage}">
            <p class="text-muted"><c:out value="${errorMessage}"/></p>
        </c:when>
        <c:otherwise>
            <p class="text-muted">An unexpected error occurred while processing your request. Please try again later.</p>
        </c:otherwise>
    </c:choose>
    <a href="${pageContext.request.contextPath}/products" class="btn btn-brand">Back to store</a>
</div>
<%@ include file="../common/footer.jspf" %>
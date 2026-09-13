<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Customers - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <h4 class="fw-bold mb-3">Customers</h4>

    <div class="card card-hover">
        <div class="table-responsive">
            <table class="table align-middle mb-0">
                <thead class="table-light">
                <tr><th>#</th><th>Username</th><th>Full name</th><th>Email</th><th>Joined</th></tr>
                </thead>
                <tbody>
                <c:forEach var="customer" items="${customers}">
                    <tr>
                        <td>${customer.userId}</td>
                        <td class="fw-semibold"><c:out value="${customer.username}"/></td>
                        <td><c:out value="${customer.fullName}"/></td>
                        <td><c:out value="${customer.email}"/></td>
                        <td class="small"><fmt:formatDate value="${customer.createdAt}" pattern="dd MMM yyyy"/></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty customers}">
                    <tr><td colspan="5" class="text-center text-muted py-4">No registered customers yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
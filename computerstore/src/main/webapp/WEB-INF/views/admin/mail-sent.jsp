<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Sent Mail - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>
<div class="container py-4">
    <div class="mail-layout">
        <div class="mail-rail">
            <a class="btn btn-brand w-100 mb-3" href="${pageContext.request.contextPath}/admin/mail/compose">Compose</a>
            <span class="mail-nav-sm-text">Mailbox</span>
            <a class="mail-nav-link" href="${pageContext.request.contextPath}/admin/mail">
                Inbox
                <span class="mail-nav-count<c:if test="${empty mailCount || mailCount == 0}"> d-none</c:if>">${mailCount}</span>
            </a>
            <a class="mail-nav-link active" href="${pageContext.request.contextPath}/admin/mail/sent">Sent</a>
        </div>
        <div class="mail-main">
            <div class="mail-list">
                <c:forEach var="m" items="${messages}">
                    <a class="mail-row" href="${pageContext.request.contextPath}/admin/mail/view?id=${m.messageId}">
                        <span class="mail-avatar-sm">${fn:substring(m.recipientName, 0, 1)}</span>
                        <div class="mail-mid">
                            <div class="mail-top">
                                <span class="fw-semibold text-truncate"><c:out value="${m.recipientName}"/></span>
                                <span class="mail-time"><fmt:formatDate value="${m.createdAt}" pattern="dd MMM, HH:mm"/></span>
                            </div>
                            <div class="text-truncate">
                                <span class="fw-semibold"><c:out value="${m.subject}"/></span>
                                <span class="mail-snippet">- <c:out value="${fn:substring(m.body, 0, 90)}"/></span>
                            </div>
                        </div>
                    </a>
                </c:forEach>
                <c:if test="${empty messages}">
                    <div class="mail-empty">No messages sent yet.</div>
                </c:if>
            </div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
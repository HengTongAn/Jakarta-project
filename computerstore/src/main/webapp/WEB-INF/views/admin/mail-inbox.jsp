<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Mail Inbox - Admin"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>
<div class="container py-4">
    <div class="mail-layout">
        <div class="mail-rail">
            <a class="btn btn-brand w-100 mb-3" href="${pageContext.request.contextPath}/admin/mail/compose">Compose</a>
            <span class="mail-nav-sm-text">Mailbox</span>
            <a class="mail-nav-link active" href="${pageContext.request.contextPath}/admin/mail">
                Inbox
                <span class="mail-nav-count<c:if test="${empty mailCount || mailCount == 0}"> d-none</c:if>">${mailCount}</span>
            </a>
            <a class="mail-nav-link" href="${pageContext.request.contextPath}/admin/mail/sent">Sent</a>
        </div>
        <div class="mail-main">
            <div class="mail-toolbar">
                <div class="btn-group btn-group-sm" role="group" aria-label="Filter inbox">
                    <a class="btn mail-filter${filter == 'all' ? ' active' : ''}" href="${pageContext.request.contextPath}/admin/mail">All</a>
                    <a class="btn mail-filter${filter == 'unread' ? ' active' : ''}" href="${pageContext.request.contextPath}/admin/mail?filter=unread">Unread (${unreadCount})</a>
                    <a class="btn mail-filter${filter == 'read' ? ' active' : ''}" href="${pageContext.request.contextPath}/admin/mail?filter=read">Read (${readCount})</a>
                </div>
                <form method="post" action="${pageContext.request.contextPath}/admin/mail/read-all">
                    <button type="submit" class="btn mail-filter<c:if test="${empty unreadCount || unreadCount == 0}"> disabled</c:if>">Mark all as read</button>
                </form>
            </div>
            <div class="mail-list" data-mail-inbox="${pageContext.request.contextPath}/admin/mail/view?id=" data-mail-filter="${filter}">
                <c:forEach var="m" items="${messages}">
                    <a class="mail-row ${not m.readFlag ? 'unread' : ''}" href="${pageContext.request.contextPath}/admin/mail/view?id=${m.messageId}">
                        <span class="mail-avatar-sm">${fn:substring(m.senderName, 0, 1)}</span>
                        <div class="mail-mid">
                            <div class="mail-top">
                                <span class="text-truncate"><c:out value="${m.senderName}"/></span>
                                <span class="mail-time"><fmt:formatDate value="${m.createdAt}" pattern="dd MMM, HH:mm"/></span>
                            </div>
                            <div class="text-truncate">
                                <span class="fw-semibold"><c:out value="${m.subject}"/></span>
                                <span class="mail-snippet">- <c:out value="${fn:substring(m.body, 0, 90)}"/></span>
                            </div>
                        </div>
                        <c:if test="${not m.readFlag}"><span class="mail-dot"></span></c:if>
                    </a>
                </c:forEach>
                <c:if test="${empty messages}">
                    <div class="mail-empty">No ${filter == 'unread' ? 'unread' : filter == 'read' ? 'read' : ''} messages here.</div>
                </c:if>
            </div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
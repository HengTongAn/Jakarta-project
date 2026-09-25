<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="peerName" value="${isRecipient ? m.senderName : m.recipientName}"/>
<c:set var="peerAvatarUrl" value="${isRecipient ? m.senderAvatarUrl : m.recipientAvatarUrl}"/>
<c:set var="pageTitle" value="Contact support — ${peerName}"/>
<%@ include file="../../layouts/header.jspf" %>
<div class="container py-4">
    <div class="chat-shell chat-shell-chat">
        <div class="chat-shell-head">
            <div class="d-flex align-items-center gap-2">
                <a class="btn btn-light btn-sm" href="${pageContext.request.contextPath}/mail" title="Back to Contact support">&larr;</a>
                <span class="chat-avatar chat-avatar-sm">
                    <c:choose>
                        <c:when test="${not empty peerAvatarUrl}">
                            <img src="${pageContext.request.contextPath}/${peerAvatarUrl}" alt="">
                        </c:when>
                        <c:otherwise>${fn:substring(peerName, 0, 1)}</c:otherwise>
                    </c:choose>
                </span>
                <div>
                    <div class="fw-bold"><c:out value="${peerName}"/></div>
                    <div class="small text-muted">
                        <c:if test="${not empty profile}">
                            @<c:out value="${profile.username}"/> &middot; ${profile.admin ? 'Administrator' : 'Support'}
                        </c:if>
                        <c:if test="${empty profile}">
                            <c:out value="${m.subject}"/>
                        </c:if>
                    </div>
                </div>
            </div>
            <div class="d-flex gap-2 align-items-center">
                <form method="post" action="${pageContext.request.contextPath}/mail/toggle">
                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                    <input type="hidden" name="id" value="${m.messageId}"/>
                    <button type="submit" class="btn btn-light btn-sm">${m.readFlag ? 'Mark unread' : 'Mark read'}</button>
                </form>
            </div>
        </div>
        <div class="chat-thread"
             data-chat-thread
             data-json="${pageContext.request.contextPath}/mail/json"
             data-sent="${pageContext.request.contextPath}/mail/sent"
             data-peer-name="${fn:escapeXml(peerName)}"
             data-inbound="${isRecipient}">
            <div class="chat-msg theirs">
                <span class="chat-avatar chat-avatar-xs">
                    <c:choose>
                        <c:when test="${not empty m.senderAvatarUrl}">
                            <img src="${pageContext.request.contextPath}/${m.senderAvatarUrl}" alt="">
                        </c:when>
                        <c:otherwise>${fn:substring(m.senderName, 0, 1)}</c:otherwise>
                    </c:choose>
                </span>
                <div>
                    <div class="chat-bubble"><c:out value="${m.body}"/></div>
                    <div class="chat-meta"><fmt:formatDate value="${m.createdAt}" pattern="dd MMM yyyy, HH:mm"/></div>
                </div>
            </div>
        </div>
        <form class="chat-composer" data-no-spinner data-chat-send action="${pageContext.request.contextPath}/mail/compose" method="post">
            <input type="hidden" name="csrfToken" value="${csrfToken}">
            <input type="hidden" name="replyTo" value="${m.messageId}"/>
            <textarea name="body" data-chat-text rows="1" placeholder="Type a message..." required></textarea>
            <button type="submit" class="btn btn-brand">Send</button>
        </form>
    </div>
</div>
<%@ include file="../../layouts/footer.jspf" %>

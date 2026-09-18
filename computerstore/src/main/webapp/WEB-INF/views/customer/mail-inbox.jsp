<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%
    java.util.List<com.example.computer_store.model.MailMessage> msgs =
            (java.util.List<com.example.computer_store.model.MailMessage>) request.getAttribute("messages");
    java.util.LinkedHashMap<Integer, com.example.computer_store.model.MailMessage> convs = new java.util.LinkedHashMap<>();
    java.util.LinkedHashMap<Integer, Integer> unreadBy = new java.util.LinkedHashMap<>();
    if (msgs != null) {
        java.util.Collections.sort(msgs, (a, b) -> Long.compare(b.getCreatedAt().getTime(), a.getCreatedAt().getTime()));
        for (com.example.computer_store.model.MailMessage msg : msgs) {
            Integer key = Integer.valueOf(msg.getSenderId());
            if (!convs.containsKey(key)) {
                convs.put(key, msg);
            }
            unreadBy.merge(key, msg.isReadFlag() ? 0 : 1, Integer::sum);
        }
    }
    request.setAttribute("_convs", convs);
    request.setAttribute("_unreadBy", unreadBy);
%>
<c:set var="pageTitle" value="Chats - TechStore"/>
<%@ include file="../common/header.jspf" %>
<div class="container py-4">
    <div class="chat-shell">
        <div class="chat-shell-head">
            <div>
                <h5 class="mb-0 fw-bold">Chats</h5>
                <span class="small text-muted">${unreadCount} unread messages</span>
            </div>
            <div class="d-flex gap-2 align-items-center">
                <form method="post" action="${pageContext.request.contextPath}/mail/read-all">
                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                    <button type="submit" class="btn btn-light btn-sm">Mark all read</button>
                </form>
                <a class="btn btn-brand btn-sm" href="${pageContext.request.contextPath}/mail/compose">New message</a>
            </div>
        </div>
        <div class="chat-conv-list"
             data-conv-list
             data-json="${pageContext.request.contextPath}/mail/json"
             data-view="${pageContext.request.contextPath}/mail/view?id=">
            <c:forEach var="entry" items="${_convs}">
                <a class="chat-conv" href="${pageContext.request.contextPath}/mail/view?id=${entry.value.messageId}">
                    <span class="chat-avatar">
                        <c:choose>
                            <c:when test="${not empty entry.value.senderAvatarUrl}">
                                <img src="${pageContext.request.contextPath}/${entry.value.senderAvatarUrl}" alt="">
                            </c:when>
                            <c:otherwise>${fn:substring(entry.value.senderName, 0, 1)}</c:otherwise>
                        </c:choose>
                    </span>
                    <div class="chat-conv-mid">
                        <div class="chat-conv-top">
                            <span class="chat-conv-name"><c:out value="${entry.value.senderName}"/></span>
                            <span class="chat-time"><fmt:formatDate value="${entry.value.createdAt}" pattern="dd MMM, HH:mm"/></span>
                        </div>
                        <span class="chat-conv-preview"><c:out value="${entry.value.subject}"/> - <c:out value="${fn:substring(entry.value.body, 0, 60)}"/></span>
                    </div>
                    <span class="chat-conv-end">
                        <c:if test="${_unreadBy[entry.key] > 0}">
                            <span class="chat-badge">${_unreadBy[entry.key]}</span>
                        </c:if>
                    </span>
                </a>
            </c:forEach>
            <c:if test="${empty _convs}">
                <div class="chat-empty">No messages yet.</div>
            </c:if>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>

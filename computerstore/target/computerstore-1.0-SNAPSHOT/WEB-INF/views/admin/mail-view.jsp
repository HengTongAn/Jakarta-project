<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="Message - Admin"/>
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
            <a class="mail-nav-link" href="${pageContext.request.contextPath}/admin/mail/sent">Sent</a>
        </div>
        <div class="mail-main p-3">
            <div class="d-flex justify-content-between align-items-center mb-3">
                <a class="btn btn-light btn-sm" href="${pageContext.request.contextPath}/admin/mail">&larr; Inbox</a>
                <div class="d-flex gap-2">
                    <c:if test="${isRecipient}">
                        <form method="post" action="${pageContext.request.contextPath}/admin/mail/toggle">
                            <input type="hidden" name="id" value="${m.messageId}"/>
                            <button type="submit" class="btn btn-light btn-sm">
                                ${m.readFlag ? 'Mark unread' : 'Mark as read'}
                            </button>
                        </form>
                    </c:if>
                    <c:if test="${isRecipient}">
                        <a class="btn btn-brand btn-sm" href="${pageContext.request.contextPath}/admin/mail/compose?replyTo=${m.messageId}">Reply</a>
                    </c:if>
                </div>
            </div>

            <div class="mail-view-card p-4">
                <h4 class="fw-bold"><c:out value="${m.subject}"/></h4>
                <div class="d-flex align-items-center gap-3 mb-3">
                    <span class="mail-avatar-sm">${fn:substring(m.senderName, 0, 1)}</span>
                    <div>
                        <div class="fw-semibold"><c:out value="${m.senderName}"/>
                            <c:if test="${m.senderUsername != null}">
                                <span class="text-muted small">&lt;<c:out value="${m.senderUsername}"/>&gt;</span>
                            </c:if>
                            <c:if test="${m.readFlag}">
                                <span class="badge text-bg-success ms-2">Read</span>
                            </c:if>
                            <c:if test="${not m.readFlag}">
                                <span class="badge text-bg-warning ms-2">Unread</span>
                            </c:if>
                        </div>
                        <div class="small text-muted">
                            to <c:out value="${m.recipientName}"/> &middot; <fmt:formatDate value="${m.createdAt}" pattern="dd MMM yyyy, HH:mm"/>
                        </div>
                    </div>
                </div>
                <hr/>
                <div class="mail-body"><c:out value="${m.body}"/></div>
            </div>

            <c:if test="${not empty profile}">
                <div class="mail-view-card p-4 mt-3">
                    <h6 class="mail-nav-sm-text mb-3">Profile</h6>
                    <div class="d-flex align-items-center gap-3">
                        <span class="mail-avatar-lg">${fn:substring(profile.fullName, 0, 1)}</span>
                        <div class="flex-grow-1">
                            <div class="fw-bold"><c:out value="${profile.fullName}"/>
                                <span class="badge ${profile.admin ? 'text-bg-primary' : 'text-bg-success'} ms-2">
                                    ${profile.admin ? 'Administrator' : 'Customer'}
                                </span>
                                <c:if test="${profile.userId == m.senderId}">
                                    <span class="badge text-bg-secondary ms-1">Sender</span>
                                </c:if>
                            </div>
                            <div class="small text-muted">@<c:out value="${profile.username}"/></div>
                            <div class="small text-muted"><c:out value="${profile.email}"/></div>
                            <div class="small text-muted">
                                Member since <fmt:formatDate value="${profile.createdAt}" pattern="dd MMM yyyy"/>
                            </div>
                        </div>
                    </div>
                </div>
            </c:if>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
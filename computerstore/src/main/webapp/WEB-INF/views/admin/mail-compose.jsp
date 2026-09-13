<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Compose - Admin"/>
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
            <h5 class="fw-bold mb-3">New message</h5>

            <c:if test="${not empty original}">
                <div class="alert alert-light border small">
                    Replying to <strong><c:out value="${original.senderName}"/></strong> (<c:out value="${original.senderUsername}"/>)
                </div>
            </c:if>

            <form action="${pageContext.request.contextPath}/admin/mail/compose" method="post">
                <c:if test="${not empty replyTo}">
                    <input type="hidden" name="replyTo" value="${replyTo}"/>
                </c:if>
                <div class="mb-3">
                    <label class="form-label">To</label>
                    <c:choose>
                        <c:when test="${not empty original}">
                            <input type="text" class="form-control" value="<c:out value='${original.senderName}'/>" disabled/>
                        </c:when>
                        <c:otherwise>
                            <select class="form-select" name="recipientId" required>
                                <option value="">Choose a customer…</option>
                                <c:forEach var="r" items="${recipients}">
                                    <option value="${r.userId}"><c:out value="${r.fullName}"/> (<c:out value="${r.username}"/>)</option>
                                </c:forEach>
                            </select>
                        </c:otherwise>
                    </c:choose>
                </div>
                <div class="mb-3">
                    <label class="form-label">Subject</label>
                    <input type="text" class="form-control" name="subject" maxlength="200" required
                           value="<c:out value='${replySubject}'/>"/>
                </div>
                <div class="mb-3">
                    <label class="form-label">Message</label>
                    <textarea class="form-control" name="body" rows="8" required></textarea>
                </div>
                <button type="submit" class="btn btn-brand">Send</button>
                <a class="btn btn-light" href="${pageContext.request.contextPath}/admin/mail">Cancel</a>
            </form>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
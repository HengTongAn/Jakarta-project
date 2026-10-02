<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- Same "X - Admin" shape every other admin view uses. The name comes from the gate
     rather than being hardcoded, so the tab, history entry and any assistive-tech
     announcement all say which section is being held back. --%>
<c:set var="pageTitle" value="${comingSoonName} - Admin"/>
<%@ include file="../layouts/header.jspf" %>
<%@ include file="../layouts/admin-nav.jspf" %>

<%-- Reached only through ComingSoonGateFilter, which forwards here for the
     gated admin paths. The nav above stays real: the forward leaves
     jakarta.servlet.forward.servlet_path pointing at the original URL, so the
     correct nav item is highlighted exactly as it would be on a live page. --%>

<div class="container py-4">
    <%-- The blurred "screen" behind the modal. Deliberately inert and hidden
         from assistive tech: it is a placeholder for a page that does not
         exist yet, so nothing in it is real content to read or click. --%>
    <div class="coming-soon-ghost" aria-hidden="true">
        <div class="d-flex justify-content-between align-items-center gap-3 mb-3">
            <div class="placeholder-glow">
                <span class="placeholder col-6 fs-4"></span>
            </div>
            <div class="placeholder-glow">
                <span class="placeholder col-4"></span>
            </div>
        </div>
        <div class="row g-3 mb-3">
            <c:forEach var="i" begin="1" end="4">
                <div class="col-md-3">
                    <div class="card card-hover">
                        <div class="card-body py-2">
                            <div class="placeholder-glow mb-2"><span class="placeholder col-7"></span></div>
                            <div class="placeholder-glow"><span class="placeholder col-5 fs-5"></span></div>
                        </div>
                    </div>
                </div>
            </c:forEach>
        </div>
        <div class="card card-hover">
            <div class="table-responsive">
                <table class="table align-middle mb-0">
                    <thead class="table-light">
                    <tr>
                        <th><span class="placeholder-glow"><span class="placeholder col-4"></span></span></th>
                        <th><span class="placeholder-glow"><span class="placeholder col-5"></span></span></th>
                        <th><span class="placeholder-glow"><span class="placeholder col-3"></span></span></th>
                        <th><span class="placeholder-glow"><span class="placeholder col-4"></span></span></th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach var="r" begin="1" end="6">
                        <tr>
                            <td><span class="placeholder-glow"><span class="placeholder col-3"></span></span></td>
                            <td><span class="placeholder-glow"><span class="placeholder col-8"></span></span></td>
                            <td><span class="placeholder-glow"><span class="placeholder col-5"></span></span></td>
                            <td><span class="placeholder-glow"><span class="placeholder col-4"></span></span></td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<%-- The popup. Bootstrap's own modal, so Esc, backdrop click, the close
     button and the focus trap all behave the way they do everywhere else in
     the app rather than being reimplemented here.

     Note: This is for the admin gate only. The checkout page has its own separate
     "coming soon" modal implementation using custom JavaScript-generated overlay
     with classes (checkout-coming-soon-*) in pages.css. The two implementations
     are intentionally separate:
     - Admin: Bootstrap's native modal (this file)
     - Checkout: Custom JavaScript overlay (app.js showComingSoonModal)

     The element ids are deliberately not the same as the request attributes
     they render (comingSoonName, comingSoonIcon, comingSoonNote): sharing
     names across the two namespaces reads as though the ids are wired to the
     attributes, when in fact each id is wired to exactly one of them. --%>
<div class="modal fade coming-soon-modal" id="comingSoonModal" tabindex="-1"
     role="dialog" aria-modal="true" aria-labelledby="comingSoonTitle" aria-describedby="comingSoonMessage">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content coming-soon-dialog">
            <div class="modal-body text-center p-4 p-md-5">
                <div class="coming-soon-icon mb-3" aria-hidden="true">
                    <i class="bi <c:out value="${comingSoonIcon}"/>"></i>
                </div>
                <h2 class="h4 fw-bold mb-2" id="comingSoonTitle">
                    <c:out value="${comingSoonName}"/> is coming soon
                </h2>
                <p class="text-muted mb-0" id="comingSoonMessage">
                    <c:out value="${comingSoonNote}"/>
                </p>
            </div>
            <div class="modal-footer justify-content-center border-0 pt-0">
                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Close</button>
                <a class="btn btn-brand" href="${pageContext.request.contextPath}/admin">Back to Dashboard</a>
            </div>
        </div>
    </div>
</div>

<%-- The bundle that provides bootstrap.Modal is loaded by footer.jspf, at the
     end of the body, so this runs before it exists. DOMContentLoaded fires
     after every deferred/parser-inserted script on the page has run, which is
     the earliest point the Modal class is guaranteed to be defined. Guarded
     anyway: if the bundle ever fails, the page still renders and the ghost
     stays visible, just without the popup. --%>
<script>
    document.addEventListener('DOMContentLoaded', function () {
        var el = document.getElementById('comingSoonModal');
        if (!el || !window.bootstrap) return;
        bootstrap.Modal.getOrCreateInstance(el).show();
    });
</script>

<%@ include file="../layouts/footer.jspf" %>

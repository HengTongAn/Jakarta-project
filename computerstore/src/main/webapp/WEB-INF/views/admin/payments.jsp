<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Payments - Admin"/>
<%@ include file="../layouts/header.jspf" %>
<%@ include file="../layouts/admin-nav.jspf" %>

<div class="container py-4">
    <div class="mb-3">
        <h4 class="fw-bold mb-1">Payment gateway</h4>
        <p class="text-muted small mb-0">
            ABA Payway. With the shipped defaults the storefront is unchanged — this page only
            controls whether the option is offered and whether it reaches a real bank.
        </p>
    </div>

    <div class="row g-3">
        <div class="col-lg-7">
            <div class="card card-hover">
                <div class="card-body">
                    <h6 class="fw-bold mb-3">Configuration</h6>

                    <div class="alert alert-warning d-flex gap-2 align-items-start" role="alert">
                        <i class="bi bi-exclamation-triangle-fill flex-shrink-0"></i>
                        <div>
                            <strong>The live path is unverified.</strong>
                            The endpoint paths and response field names in the ABA client were written from
                            publicly circulated documentation and have never been run against an ABA sandbox,
                            because this build has no merchant credentials. Check them against ABA's official
                            developer docs before enabling live mode, or the first real charge will fail.
                        </div>
                    </div>

                    <form method="post" action="${pageContext.request.contextPath}/admin/payments">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">

                        <%-- Each switch is paired with an explicit hidden "off".
                             An unchecked checkbox submits nothing at all, which the
                             server cannot tell apart from a field that never
                             arrived: unchecking "simulate" would silently leave it
                             on, and the store would keep demoing payments the
                             operator believes are real. The hidden value makes the
                             intent explicit either way, and the server treats
                             "on" as the only truth. --%>
                        <div class="form-check form-switch mb-1">
                            <input class="form-check-input" type="checkbox" role="switch" name="enabled" value="on" id="enabled"${enabled ? ' checked' : ''}>
                            <input type="hidden" name="enabled" value="off">
                            <label class="form-check-label fw-semibold" for="enabled">Offer ABA Payway at checkout</label>
                        </div>
                        <div class="form-text mb-3">When off, the option does not appear and nothing can be started.</div>

                        <div class="form-check form-switch mb-1">
                            <input class="form-check-input" type="checkbox" role="switch" name="simulate" value="on" id="simulate"${simulate ? ' checked' : ''}>
                            <input type="hidden" name="simulate" value="off">
                            <label class="form-check-label fw-semibold" for="simulate">Simulate (no real bank)</label>
                        </div>
                        <div class="form-text mb-3">
                            Leave this on for a demo. The full order and payment flow runs, the QR page renders,
                            confirmation works &mdash; but no network call is made. Turn it off only once the
                            credentials below are real.
                        </div>

                        <hr>

                        <div class="mb-3">
                            <label class="form-label" for="merchantId">Merchant ID <span class="text-muted fw-normal">(shopId)</span></label>
                            <input type="text" id="merchantId" name="merchantId" class="form-control" value="<c:out value='${merchantId}'/>" spellcheck="false" autocomplete="off" placeholder="From your ABA merchant account">
                        </div>

                        <div class="mb-3">
                            <label class="form-label" for="apiUrl">API base URL</label>
                            <input type="url" id="apiUrl" name="apiUrl" class="form-control" value="<c:out value='${apiUrl}'/>" spellcheck="false" autocomplete="off">
                            <div class="form-text">Sandbox host by default. Swap for the production host when you go live.</div>
                        </div>

                        <div class="mb-3">
                            <label class="form-label" for="username">API username <span class="text-muted fw-normal">(aba-username)</span></label>
                            <input type="text" id="username" name="username" class="form-control" value="<c:out value='${username}'/>" spellcheck="false" autocomplete="off">
                        </div>

                        <%-- Computed before the input: a custom tag cannot live
                             inside another tag's attribute value. --%>
                        <c:set var="secretHint" value="${secretSet ? 'Stored - leave blank to keep' : 'Not set yet'}"/>
                        <div class="mb-3">
                            <label class="form-label" for="secret">API secret <span class="text-muted fw-normal">(aba-secret)</span></label>
                            <input type="password" id="secret" name="secret" class="form-control" value="" placeholder="<c:out value='${secretHint}'/>" autocomplete="new-password">
                            <div class="form-text">
                                Write-only. Never displayed again, and it stays on the server &mdash; it is never sent to a browser.
                            </div>
                        </div>

                        <div class="row g-3">
                            <div class="col-md-6">
                                <label class="form-label" for="shopName">Shop name</label>
                                <input type="text" id="shopName" name="shopName" class="form-control" value="<c:out value='${shopName}'/>">
                            </div>
                            <div class="col-md-6">
                                <label class="form-label" for="currency">Currency</label>
                                <input type="text" id="currency" name="currency" class="form-control" value="<c:out value='${currency}'/>" maxlength="3" spellcheck="false">
                                <div class="form-text">Must match how the store prices its products.</div>
                            </div>
                        </div>

                        <hr class="my-4">

                        <h6 class="fw-bold mb-1">Visa card</h6>
                        <p class="text-muted small mb-3">
                            A separate provider with its own switches, so turning card off cannot disturb
                            the ABA configuration above and vice versa.
                        </p>

                        <div class="form-check form-switch mb-1">
                            <input class="form-check-input" type="checkbox" role="switch" name="cardEnabled" value="on" id="cardEnabled"${cardEnabled ? ' checked' : ''}>
                            <input type="hidden" name="cardEnabled" value="off">
                            <label class="form-check-label fw-semibold" for="cardEnabled">Offer card payment at checkout</label>
                        </div>
                        <div class="form-text mb-3">When off, the option does not appear and no card can be submitted.</div>

                        <div class="form-check form-switch mb-1">
                            <input class="form-check-input" type="checkbox" role="switch" name="cardSimulate" value="on" id="cardSimulate"${cardSimulate ? ' checked' : ''}>
                            <input type="hidden" name="cardSimulate" value="off">
                            <label class="form-check-label fw-semibold" for="cardSimulate">Simulate (no real charge)</label>
                        </div>

                        <%-- The honest bit: there is no acquirer wired up, so a live
                             card configuration cannot work. Saying so here is the
                             point -- a switch that silently does nothing would be
                             worse than one that explains itself. --%>
                        <div class="alert alert-warning small mb-3" role="note">
                            <i class="bi bi-exclamation-triangle-fill me-1"></i>
                            <strong>Simulation is the only card mode that works.</strong>
                            A store cannot call Visa directly &mdash; card payments go through an
                            acquirer or gateway, and none is integrated yet. If you turn simulation off,
                            card payment is withheld from checkout rather than offered and then failing.
                            <c:if test="${not cardReady and not empty cardBlockingReason}">
                                <br><c:out value="${cardBlockingReason}"/>
                            </c:if>
                        </div>

                        <div class="alert alert-light small mb-3" role="note">
                            <i class="bi bi-shield-lock-fill me-1"></i>
                            <strong>Card numbers are never stored.</strong>
                            The demo form accepts only published test numbers, and a real card is
                            rejected before it goes anywhere. What gets saved is the brand and the
                            last four digits; the database will not accept a longer value.
                        </div>

                        <button type="submit" class="btn btn-brand w-100 mt-4">
                            <i class="bi bi-check2 me-1"></i> Save payment settings
                        </button>
                    </form>
                </div>
            </div>
        </div>

        <div class="col-lg-5">
            <div class="card card-hover">
                <div class="card-body">
                    <h6 class="fw-bold mb-3">Current state</h6>

                    <div class="d-flex align-items-center gap-2 mb-3">
                        <c:choose>
                        <c:when test="${ready}">
                            <span class="badge bg-success"><i class="bi bi-broadcast me-1"></i>LIVE — real charges enabled</span>
                        </c:when>
                        <c:when test="${enabled and simulate}">
                            <span class="badge bg-primary"><i class="bi bi-play-circle me-1"></i>Simulation — no real charges</span>
                        </c:when>
                        <c:when test="${enabled}">
                            <span class="badge bg-danger"><i class="bi bi-exclamation-octagon me-1"></i>Enabled but not usable</span>
                        </c:when>
                        <c:otherwise>
                            <span class="badge bg-secondary"><i class="bi bi-slash-circle me-1"></i>Off — not offered at checkout</span>
                        </c:otherwise>
                        </c:choose>
                    </div>

                    <c:if test="${not empty blockingReason}">
                        <p class="text-muted small mb-3"><c:out value="${blockingReason}"/></p>
                    </c:if>

                    <%-- "not set" is a <c:choose> rather than an EL ternary on purpose.
                         Putting markup inside a ${...} in template text does not work:
                         the JSP parser sees the <span> as a real tag, so the
                         expression is never evaluated and the raw HTML is emitted
                         verbatim. That made this table report "not set" for a secret
                         that was in fact stored. --%>
                    <table class="table table-sm align-middle mb-0">
                        <tbody>
                            <tr><td class="text-muted">Offered at checkout</td><td class="text-end fw-semibold">${enabled ? 'Yes' : 'No'}</td></tr>
                            <tr><td class="text-muted">Mode</td><td class="text-end fw-semibold">${simulate ? 'Simulated' : 'Live'}</td></tr>
                            <tr><td class="text-muted">Merchant ID</td><td class="text-end fw-semibold">
                                <c:choose>
                                <c:when test="${empty merchantId}"><span class="text-muted fw-normal">not set</span></c:when>
                                <c:otherwise><c:out value="${merchantId}"/></c:otherwise>
                                </c:choose>
                            </td></tr>
                            <tr><td class="text-muted">API secret</td><td class="text-end fw-semibold">
                                <c:choose>
                                <c:when test="${secretSet}"><i class="bi bi-check-lg text-success me-1"></i>Set</c:when>
                                <c:otherwise><span class="text-muted fw-normal">not set</span></c:otherwise>
                                </c:choose>
                            </td></tr>
                            <tr><td class="text-muted">API username</td><td class="text-end fw-semibold">
                                <c:choose>
                                <c:when test="${empty username}"><span class="text-muted fw-normal">not set</span></c:when>
                                <c:otherwise><c:out value="${username}"/></c:otherwise>
                                </c:choose>
                            </td></tr>
                            <tr><td class="text-muted">API base URL</td><td class="text-end fw-semibold"><code><c:out value="${apiUrl}"/></code></td></tr>
                            <tr><td class="text-muted">Currency</td><td class="text-end fw-semibold"><c:out value="${currency}"/></td></tr>
                        </tbody>
                    </table>

                    <hr>

                    <h6 class="fw-bold mb-2">Card payment</h6>
                    <div class="d-flex align-items-center gap-2 mb-2">
                        <c:choose>
                        <c:when test="${cardEnabled and cardSimulate}">
                            <span class="badge bg-primary"><i class="bi bi-play-circle me-1"></i>Simulation — test cards only</span>
                        </c:when>
                        <c:when test="${cardEnabled}">
                            <span class="badge bg-danger"><i class="bi bi-exclamation-octagon me-1"></i>Enabled but not usable</span>
                        </c:when>
                        <c:otherwise>
                            <span class="badge bg-secondary"><i class="bi bi-slash-circle me-1"></i>Off — not offered at checkout</span>
                        </c:otherwise>
                        </c:choose>
                    </div>
                    <c:if test="${not empty cardBlockingReason}">
                        <p class="text-muted small mb-2"><c:out value="${cardBlockingReason}"/></p>
                    </c:if>

                    <hr>

                    <h6 class="fw-bold mb-2">Before going live, check</h6>
                    <ul class="text-muted small mb-0 ps-3">
                        <li>Endpoint paths against ABA's official docs</li>
                        <li>Response field names (<code>transId</code> vs <code>TRANS_ID</code>)</li>
                        <li>The currency your merchant agreement settles in</li>
                        <li>That the site is served over HTTPS — live credentials must not cross plain HTTP</li>
                        <li>That the webhook/return host ABA calls is publicly reachable</li>
                    </ul>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../layouts/footer.jspf" %>

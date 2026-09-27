<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Checkout - Apach_PC/STORE"/>
<%@ include file="../layouts/header.jspf" %>
<div class="container py-4 checkout-page">
    <div class="checkout-heading d-flex justify-content-between align-items-start gap-3 mb-1">
        <div>
            <p class="eyebrow mb-1"><i class="bi bi-shield-lock-fill me-1"></i>Secure checkout</p>
            <h4 class="fw-bold mb-1">Complete your order</h4>
            <p class="text-muted mb-0">Review your details and choose how you would like to pay.</p>
        </div>
        <span class="checkout-secure-badge"><i class="bi bi-lock-fill"></i> Protected checkout</span>
    </div>
    <div class="checkout-steps mb-4" aria-label="Checkout progress">
        <span class="step done"><i class="bi bi-cart-check-fill"></i><span class="d-none d-sm-inline"> Cart</span></span>
        <span class="step-bar done"></span>
        <span class="step active"><i class="bi bi-credit-card-2-front"></i><span class="d-none d-sm-inline"> Checkout</span></span>
        <span class="step-bar"></span>
        <span class="step"><i class="bi bi-check-circle"></i><span class="d-none d-sm-inline"> Confirmed</span></span>
    </div>

    <form method="post" action="${pageContext.request.contextPath}/checkout" id="checkoutForm" class="row g-3">
        <div class="col-lg-8">
            <div class="card card-hover checkout-panel">
                <div class="card-body">
                    <div class="checkout-panel-heading">
                        <span class="checkout-number">1</span>
                        <div><h6 class="fw-bold mb-1">Shipping &amp; billing details</h6><p class="text-muted small mb-0">Where should we send your order?</p></div>
                    </div>
                    <div class="row g-3">
                        <div class="col-md-6">
                            <label class="form-label">Full name</label>
                            <input type="text" class="form-control" value="<c:out value='${sessionScope.user.fullName}'/>" readonly>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">Email</label>
                            <input type="text" class="form-control" value="<c:out value='${sessionScope.user.email}'/>" readonly>
                        </div>
                        <div class="col-12">
                            <label class="form-label" for="deliveryAddress">Delivery address <span class="text-muted fw-normal">(demo)</span></label>
                            <textarea id="deliveryAddress" name="deliveryAddress" class="form-control" rows="2" placeholder="House number, street, city"></textarea>
                            <div class="form-text">Collected for the checkout demo UI only — this build does not persist shipping details.</div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="card card-hover mt-3 checkout-panel">
                <div class="card-body">
                    <div class="checkout-panel-heading mb-3">
                        <span class="checkout-number">2</span>
                        <div><h6 class="fw-bold mb-1">Payment method</h6><p class="text-muted small mb-0">Choose your preferred payment option.</p></div>
                    </div>
                    <%-- Up to three providers, each with its own switch. The note has
                         to describe the *combination* that is actually offered, not
                         one provider: with only the card method on there is nothing
                         to say about a QR code. --%>
                    <c:set var="anyOnline" value="${abaAvailable or cardAvailable}"/>
                    <c:set var="anyDemo" value="${(abaAvailable and abaSimulated) or (cardAvailable and cardSimulated)}"/>
                    <c:choose>
                    <c:when test="${not anyOnline}">
                        <div class="payment-demo-note" role="note"><i class="bi bi-info-circle-fill"></i><span><strong>Cash on delivery.</strong> No online payment is switched on for this store. An administrator can enable ABA Payway or card payment under Admin &rsaquo; Payments.</span></div>
                    </c:when>
                    <c:when test="${anyDemo}">
                        <div class="payment-demo-note" role="note"><i class="bi bi-info-circle-fill"></i><span><strong>Simulation mode.</strong> The online methods below run against a local simulator &mdash; the order, the payment record and the confirmation are all real, but no bank is contacted and no money moves.</span></div>
                    </c:when>
                    <c:otherwise>
                        <div class="payment-demo-note" role="note"><i class="bi bi-shield-lock-fill"></i><span><strong>Pay by card or ABA Payway.</strong> Your order is only confirmed as paid once the payment provider reports success.</span></div>
                    </c:otherwise>
                    </c:choose>
                    <%-- Exactly one method is selected server-side and its panel is
                         visible, so the page is correct before any JavaScript runs.
                         app.js then takes over the switching. --%>
                    <c:set var="defaultMethod" value="${abaAvailable ? 'aba' : (cardAvailable ? 'visa' : 'cash')}"/>
                    <div class="payment-methods" role="radiogroup" aria-label="Payment method">
                        <c:if test="${abaAvailable}">
                        <label class="payment-method${defaultMethod == 'aba' ? ' is-selected' : ''}">
                            <input type="radio" name="paymentMethod" value="aba"${defaultMethod == 'aba' ? ' checked' : ''}>
                            <span class="payment-method-icon"><i class="bi bi-qr-code-scan"></i></span>
                            <span class="payment-method-copy"><strong>ABA Payway</strong><small>Scan a QR code in the ABA app</small></span>
                            <i class="bi bi-check-circle-fill payment-method-check"></i>
                        </label>
                        </c:if>
                        <c:if test="${cardAvailable}">
                        <label class="payment-method${defaultMethod == 'visa' ? ' is-selected' : ''}">
                            <input type="radio" name="paymentMethod" value="visa"${defaultMethod == 'visa' ? ' checked' : ''}>
                            <span class="payment-method-icon payment-method-icon-visa"><i class="bi bi-credit-card-2-front"></i></span>
                            <span class="payment-method-copy"><strong>Visa card</strong><small>Pay now with a credit or debit card</small></span>
                            <i class="bi bi-check-circle-fill payment-method-check"></i>
                        </label>
                        </c:if>
                        <label class="payment-method${defaultMethod == 'cash' ? ' is-selected' : ''}">
                            <input type="radio" name="paymentMethod" value="cash"${defaultMethod == 'cash' ? ' checked' : ''}>
                            <span class="payment-method-icon payment-method-icon-muted"><i class="bi bi-cash-stack"></i></span>
                            <span class="payment-method-copy"><strong>Cash on delivery</strong><small>Pay when your order arrives</small></span>
                            <i class="bi bi-check-circle-fill payment-method-check"></i>
                        </label>
                    </div>
                    <c:if test="${abaAvailable}">
                    <div data-payment-panel="aba" class="aba-payment-panel mt-3${defaultMethod == 'aba' ? '' : ' d-none'}">
                        <div class="cash-payment-copy"><i class="bi bi-phone"></i><div><strong>Pay in the ABA app</strong><p class="mb-0 text-muted small">After you place the order we show a QR code and transaction id. Open ABA, scan it, and confirm &mdash; this page then checks with ABA and marks the order paid.</p></div></div>
                    </div>
                    </c:if>
                    <c:if test="${cardAvailable}">
                    <div data-payment-panel="visa" class="card-payment-panel mt-3${defaultMethod == 'visa' ? '' : ' d-none'}">
                        <c:if test="${cardSimulated}">
                        <div class="card-demo-warning" role="note">
                            <i class="bi bi-exclamation-triangle-fill"></i>
                            <div>
                                <strong>Demo only &mdash; do not enter a real card.</strong>
                                This form accepts published test card numbers and nothing else.
                                Anything you type is checked and discarded: it is never stored, never logged and never sent anywhere.
                            </div>
                        </div>
                        </c:if>
                        <div class="row g-3">
                            <div class="col-12">
                                <label class="form-label" for="cardName">Name on card</label>
                                <%-- data-active-required, not required.
                                     These four inputs live in the same <form> as the
                                     other payment methods, and this panel is hidden
                                     with d-none whenever ABA or cash is selected.
                                     HTML5 constraint validation does not exempt a
                                     control for being display:none, so a statically
                                     "required" field here is still validated: the
                                     browser refuses to submit the whole form and
                                     cannot show an error on an invisible control, so
                                     choosing ABA or cash silently did nothing.
                                     initCheckoutPayment applies required to this
                                     panel only while it is the active one. The server
                                     re-validates every field regardless. --%>
                                <input type="text" class="form-control" id="cardName" name="cardName"
                                       autocomplete="off" spellcheck="false" maxlength="60"
                                       placeholder="SOK SOVANN" data-active-required="true">
                            </div>
                            <div class="col-12">
                                <label class="form-label" for="cardNumber">Card number</label>
                                <div class="input-group">
                                    <span class="input-group-text"><i class="bi bi-credit-card-2-front"></i></span>
                                    <input type="text" class="form-control" id="cardNumber" name="cardNumber"
                                           inputmode="numeric" autocomplete="off" spellcheck="false"
                                           maxlength="23" placeholder="4242 4242 4242 4242"
                                           data-active-required="true" data-luhn-target="true">
                                </div>
                                <div class="form-text" id="cardNumberFeedback" role="status"></div>
                            </div>
                            <div class="col-6">
                                <label class="form-label" for="cardExpiry">Expiry</label>
                                <input type="text" class="form-control" id="cardExpiry" name="cardExpiry"
                                       inputmode="numeric" autocomplete="off" spellcheck="false"
                                       maxlength="7" placeholder="MM/YY" data-active-required="true">
                            </div>
                            <div class="col-6">
                                <label class="form-label" for="cardCvv">Security code</label>
                                <div class="input-group">
                                    <input type="password" class="form-control" id="cardCvv" name="cardCvv"
                                           inputmode="numeric" autocomplete="off" maxlength="4"
                                           placeholder="123" data-active-required="true">
                                    <span class="input-group-text" data-toggle-cvv="true" role="button" tabindex="0"
                                          aria-label="Show security code"><i class="bi bi-eye"></i></span>
                                </div>
                            </div>
                        </div>
                        <c:if test="${cardSimulated}">
                        <details class="card-test-cards">
                            <summary>Which card numbers can I use?</summary>
                            <ul class="mb-0">
                                <c:forEach var="testCard" items="${cardTestCards}">
                                <li><code>${testCard.key}</code> &rarr; ending ${testCard.value}</li>
                                </c:forEach>
                            </ul>
                            <p class="mb-0 mt-2 text-muted small">The first one is approved; the rest are declined, so you can show a failed payment too.</p>
                        </details>
                        </c:if>
                    </div>
                    </c:if>
                    <div data-payment-panel="cash" class="cash-payment-panel mt-3${anyOnline ? ' d-none' : ''}"><div class="cash-payment-copy"><i class="bi bi-box-seam"></i><div><strong>Pay on delivery</strong><p class="mb-0 text-muted small">Have the exact amount ready when your package arrives.</p></div></div></div>
                    <div class="payment-trust-row"><span><i class="bi bi-lock-fill"></i> Encrypted checkout</span><span><i class="bi bi-shield-check"></i> Safe &amp; private</span><span><i class="bi bi-headset"></i> Support available</span></div>
                </div>
            </div>
            <div class="card card-hover mt-3 checkout-panel">
                <div class="card-body p-0">
                    <div class="table-responsive">
                        <table class="table align-middle mb-0">
                            <thead class="table-light">
                            <tr><th>Product</th><th class="text-center">Qty</th><th class="text-center">Unit price</th><th class="text-end">Subtotal</th></tr>
                            </thead>
                            <tbody>
                            <c:forEach var="item" items="${items}">
                                <tr>
                                    <td><c:out value="${item.product.name}"/></td>
                                    <td class="text-center">${item.quantity}</td>
                                    <td class="text-center money">$<fmt:formatNumber value="${item.product.price}" pattern="#,##0.00"/></td>
                                    <td class="text-end money">$<fmt:formatNumber value="${item.product.price * item.quantity}" pattern="#,##0.00"/></td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-lg-4">
            <div class="card card-hover order-summary-box sticky-top" style="top: 5.5rem;">
                <div class="card-body">
                    <div class="d-flex align-items-center justify-content-between mb-3"><h6 class="fw-bold mb-0">Order summary</h6><span class="summary-secure"><i class="bi bi-shield-check"></i> Secure</span></div>
                    <div class="d-flex justify-content-between mb-2">
                        <span>Items</span><span>${items.size()}</span>
                    </div>
                    <div class="d-flex justify-content-between mb-2">
                        <span>Shipping</span><span>Free</span>
                    </div>
                    <hr>
                    <div class="d-flex justify-content-between fw-bold fs-5">
                        <span>Total</span>
                        <span class="money">$<fmt:formatNumber value="${total}" pattern="#,##0.00"/></span>
                    </div>
                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                    <button type="submit" class="btn btn-brand w-100 btn-lg checkout-submit"><i class="bi bi-lock-fill me-2"></i>Place order securely</button>
                    <c:choose>
                    <c:when test="${abaAvailable and abaSimulated}">
                    <p class="checkout-demo-copy"><i class="bi bi-info-circle me-1"></i>Simulation mode — no money will move.</p>
                    </c:when>
                    <c:otherwise>
                    <p class="checkout-demo-copy"><i class="bi bi-shield-check me-1"></i>Your payment details are never stored on this site.</p>
                    </c:otherwise>
                    </c:choose>
                    <a href="${pageContext.request.contextPath}/cart" class="btn btn-outline-secondary w-100 mt-2">Back to cart</a>
                </div>
            </div>
        </div>
    </form>
</div>
<%@ include file="../layouts/footer.jspf" %>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Checkout - TechStore"/>
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
                    <div class="payment-demo-note" role="note"><i class="bi bi-info-circle-fill"></i><span><strong>Payment preview mode.</strong> This interface is ready for gateway integration. No real card is charged yet.</span></div>
                    <div class="payment-methods" role="radiogroup" aria-label="Payment method">
                        <label class="payment-method is-selected">
                            <input type="radio" name="paymentMethod" value="card" checked>
                            <span class="payment-method-icon"><i class="bi bi-credit-card-2-front-fill"></i></span>
                            <span class="payment-method-copy"><strong>Credit or debit card</strong><small>Visa, Mastercard, Amex</small></span>
                            <i class="bi bi-check-circle-fill payment-method-check"></i>
                        </label>
                        <label class="payment-method">
                            <input type="radio" name="paymentMethod" value="cash">
                            <span class="payment-method-icon payment-method-icon-muted"><i class="bi bi-cash-stack"></i></span>
                            <span class="payment-method-copy"><strong>Cash on delivery</strong><small>Pay when your order arrives</small></span>
                            <i class="bi bi-check-circle-fill payment-method-check"></i>
                        </label>
                    </div>
                    <div id="cardPaymentPanel" class="card-payment-panel mt-3">
                        <div class="row g-3">
                            <div class="col-12"><label class="form-label" for="cardNumber">Card number</label><div class="input-icon-wrap"><i class="bi bi-credit-card-2-front" aria-hidden="true"></i><input id="cardNumber" class="form-control" type="text" inputmode="numeric" autocomplete="cc-number" placeholder="1234 5678 9012 3456" maxlength="19"><span class="card-brands">VISA&nbsp; MC</span></div></div>
                            <div class="col-12 col-sm-7"><label class="form-label" for="cardName">Name on card</label><input id="cardName" class="form-control" type="text" autocomplete="cc-name" placeholder="Alex Morgan"></div>
                            <div class="col-6 col-sm-3"><label class="form-label" for="cardExpiry">Expiry</label><input id="cardExpiry" class="form-control" type="text" inputmode="numeric" autocomplete="cc-exp" placeholder="MM/YY" maxlength="5"></div>
                            <div class="col-6 col-sm-2"><label class="form-label" for="cardCvc">CVC</label><input id="cardCvc" class="form-control" type="password" inputmode="numeric" autocomplete="cc-csc" placeholder="•••" maxlength="4"></div>
                        </div>
                    </div>
                    <div class="cash-payment-panel mt-3 d-none"><div class="cash-payment-copy"><i class="bi bi-box-seam"></i><div><strong>Pay on delivery</strong><p class="mb-0 text-muted small">Have the exact amount ready when your package arrives.</p></div></div></div>
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
                    <p class="checkout-demo-copy"><i class="bi bi-info-circle me-1"></i>Demo mode — no payment will be charged.</p>
                    <a href="${pageContext.request.contextPath}/cart" class="btn btn-outline-secondary w-100 mt-2">Back to cart</a>
                </div>
            </div>
        </div>
    </form>
</div>
<%@ include file="../layouts/footer.jspf" %>

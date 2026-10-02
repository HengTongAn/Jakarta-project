package com.hengtongan.computerstore.core.config;

import com.hengtongan.computerstore.core.repository.AppSettingsRepository;

import java.util.Locale;

/**
 * Payment gateway configuration, read from {@code app_settings} so an admin can
 * switch providers on without a rebuild or a restart.
 * <p>
 * Two independent switches, and the distinction matters:
 * <ul>
 * <li><b>enabled</b> &mdash; whether ABA Payway appears as a payment option at
 * all. Off by default: no option, no QR page, nothing can be started.</li>
 * <li><b>simulate</b> &mdash; whether a started payment talks to the real
 * gateway. On by default, so a demo or a fresh checkout exercises the whole
 * order state machine without credentials or network access.</li>
 * </ul>
 * With the shipped defaults ({@code enabled=false}, {@code simulate=true}) the
 * storefront behaves exactly as it did before this feature existed.
 * <p>
 * The API secret never leaves the server: nothing in this class is written to a
 * request attribute that a view renders, and {@link #isReady()} is the only
 * thing the admin page is told.
 */
public final class PaymentConfig {

    public static final String KEY_ENABLED = "payment.aba.enabled";
    public static final String KEY_SIMULATE = "payment.aba.simulate";
    public static final String KEY_MERCHANT_ID = "payment.aba.merchant_id";
    public static final String KEY_API_URL = "payment.aba.api_url";
    public static final String KEY_USERNAME = "payment.aba.username";
    public static final String KEY_SECRET = "payment.aba.secret";
    public static final String KEY_SHOP_NAME = "payment.aba.shop_name";
    public static final String KEY_CURRENCY = "payment.aba.currency";

    // Card payments are a separate provider with its own switches, so turning
    // card off cannot disturb an ABA configuration and vice versa.
    public static final String KEY_CARD_ENABLED = "payment.card.enabled";
    public static final String KEY_CARD_SIMULATE = "payment.card.simulate";

    private static final String DEFAULT_API_URL = "https://api.abapayway.com";
    private static final String DEFAULT_SHOP_NAME = "Apach_PC/STORE";
    private static final String DEFAULT_CURRENCY = "USD";

    /**
     * One read of the whole settings bag per short window. Without this the
     * checkout page alone would issue one query per accessor, because each call
     * to {@link #get} reloads the table. {@link #invalidate()} drops it the
     * moment an admin saves, so the cache never delays a configuration change.
     */
    private static final com.github.benmanes.caffeine.cache.Cache<String, java.util.Map<String, String>> CACHE
	    = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
		    .maximumSize(1)
		    .expireAfterWrite(30, java.util.concurrent.TimeUnit.SECONDS)
		    .build();

    private PaymentConfig() {
    }

    /**
     * Drops the cached settings. Called after the admin saves the form.
     */
    public static void invalidate() {
	CACHE.invalidateAll();
    }

    private static java.util.Map<String, String> all() {
	return CACHE.get("app_settings", k -> new AppSettingsRepository().findAll());
    }

    public static String get(String key, String fallback) {
	String value = all().get(key);
	return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static boolean isEnabled() {
	return bool(KEY_ENABLED, false);
    }

    /**
     * True when a started payment must stop at the gateway boundary and be
     * resolved locally instead. Deliberately the default, so a half-configured
     * store degrades to a working demo rather than to broken checkouts.
     */
    public static boolean isSimulate() {
	return bool(KEY_SIMULATE, true);
    }

    public static boolean isLiveEnabled() {
	return isEnabled() && !isSimulate();
    }

    public static String merchantId() {
	return get(KEY_MERCHANT_ID, "");
    }

    public static String apiUrl() {
	return trimTrailingSlash(get(KEY_API_URL, DEFAULT_API_URL));
    }

    public static String username() {
	return get(KEY_USERNAME, "");
    }

    /**
     * Server-side only. Never expose this to a view.
     */
    public static String secret() {
	return get(KEY_SECRET, "");
    }

    public static String shopName() {
	return get(KEY_SHOP_NAME, DEFAULT_SHOP_NAME);
    }

    public static String currency() {
	return get(KEY_CURRENCY, DEFAULT_CURRENCY).toUpperCase(Locale.ROOT);
    }

    /**
     * True only when a live charge is possible: enabled, not simulated,
     * credentialed.
     */
    public static boolean isReady() {
	return isLiveEnabled()
		&& !merchantId().isBlank()
		&& !username().isBlank()
		&& !secret().isBlank();
    }

    /**
     * Why {@link #isReady()} is false, phrased for an admin. Returns
     * {@code null} when the configuration is usable.
     */
    public static String blockingReason() {
	if (!isEnabled()) {
	    return "ABA Payway is switched off, so it is not offered at checkout.";
	}
	if (isSimulate()) {
	    return null;
	}
	if (merchantId().isBlank() || username().isBlank() || secret().isBlank()) {
	    return "Live mode is on but the merchant ID, API username or API secret is missing.";
	}
	return null;
    }

    // ---------------------------------------------------------- card method
    /**
     * Whether the Visa card option appears at checkout. Off unless switched on.
     */
    public static boolean isCardEnabled() {
	return bool(KEY_CARD_ENABLED, false);
    }

    /**
     * Whether a card submission is settled locally instead of being sent to a
     * card gateway. Defaults to true, and today it is the only mode that
     * exists.
     */
    public static boolean isCardSimulated() {
	return bool(KEY_CARD_SIMULATE, true);
    }

    /**
     * True when a card payment can actually be taken.
     * <p>
     * Deliberately narrower than {@link #isCardEnabled()}: switching card on
     * with simulation off is a configuration that cannot charge anyone, so the
     * option is withheld rather than offered and then failing at the last step.
     * This mirrors how an enabled-but-credentialless ABA configuration is
     * handled.
     */
    public static boolean isCardAvailable() {
	return isCardEnabled() && (isCardSimulated() || isCardReady());
    }

    /**
     * True only when a real card gateway is configured.
     * <p>
     * Always false, and that is the honest answer: a store cannot call Visa
     * directly, it needs an acquirer, and none is integrated. The method exists
     * so the admin page can state the limitation instead of leaving a switch
     * that implies a capability the code does not have. Wiring up an acquirer
     * means giving this a real readiness check and adding a client alongside
     * {@code AbaPaywayClient}.
     */
    public static boolean isCardReady() {
	return false;
    }

    /**
     * Why a card payment cannot be taken right now, phrased for an admin.
     * Returns {@code null} when the option is usable.
     */
    public static String cardBlockingReason() {
	if (!isCardEnabled()) {
	    return "Card payments are switched off, so they are not offered at checkout.";
	}
	if (!isCardAvailable()) {
	    return "Simulation is off but no card gateway is configured, so no card payment can be taken.";
	}
	return null;
    }

    private static boolean bool(String key, boolean fallback) {
	String value = all().get(key);
	if (value == null || value.isBlank()) {
	    return fallback;
	}
	return "true".equalsIgnoreCase(value.trim());
    }

    private static String trimTrailingSlash(String url) {
	String value = url.trim();
	while (value.endsWith("/")) {
	    value = value.substring(0, value.length() - 1);
	}
	return value;
    }
}

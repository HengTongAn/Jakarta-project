package com.hengtongan.computerstore.core.domain.entity;

/**
 * One sampled page view, as the browser experienced it.
 *
 * <p>Every timing here was measured in a browser and reported back by
 * {@code rum.js}. The split between them is the point of the type: if the server
 * think time is 90 ms and the page did not become clickable until 3000 ms, then
 * the server is not the problem and the connection or the page weight is. One
 * combined figure could not tell an operator which of the two to go and fix, so
 * they are kept apart rather than averaged into a number that means neither.</p>
 *
 * <p>Not present, deliberately: ip, user id, session id, full url, user agent. See
 * {@code PageType} and {@code UserAgentClassifier} for why, and for the reason this
 * type is safe to aggregate without a data-protection review of the columns.
 */
public class PageExperienceSample {

    /** Bounded route template, e.g. {@code /products} or {@code /products:detail}. */
    private String pageType;
    /** Coarse browser family, e.g. {@code Chrome}. Never a full User-Agent. */
    private String browser;
    /** Coarse device class: desktop, mobile, tablet, unknown. */
    private String device;

    /**
     * Server think time as the browser experienced it: responseStart minus
     * requestStart, so connection setup is excluded. Not the server's own
     * in-container timing, which no script is able to read.
     */
    private int serverMs;
    /** Time to first byte from navigation start, so including connection setup. */
    private int ttfbMs;

    /**
     * Browser-observed milliseconds from navigation start, or a negative value when
     * the browser did not report. Negative rather than a nullable Integer so the
     * arithmetic in the aggregations cannot silently produce null sums.
     *
     * <p>Defaults to {@code -1}, not {@code 0}, and that matters: a
     * {@code plain int} field would default to zero, which is both a plausible
     * real latency and indistinguishable from a reported one. A sample built and
     * stored without touching these would then claim a browser measured a
     * 0&nbsp;ms page, and the report would show a fast page nobody experienced.</p>
     */
    private int interactiveMs = UNREPORTED;
    /** Browser-observed DOMContentLoaded, in ms. Negative when unreported. */
    private int domReadyMs = UNREPORTED;
    /** Browser-observed load event, in ms. Negative when unreported. */
    private int loadMs = UNREPORTED;

    /** Sentinel for "this phase was not reported", distinct from a real 0 ms. */
    public static final int UNREPORTED = -1;

    /** Encoded response size in bytes, as the browser measured it. */
    private int transferBytes;

    public PageExperienceSample() {
    }

    public PageExperienceSample(String pageType, String browser, String device) {
        this.pageType = pageType;
        this.browser = browser;
        this.device = device;
    }

    public String getPageType() {
        return pageType;
    }

    public void setPageType(String pageType) {
        this.pageType = pageType;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public int getServerMs() {
        return serverMs;
    }

    public void setServerMs(int serverMs) {
        this.serverMs = serverMs;
    }

    public int getTtfbMs() {
        return ttfbMs;
    }

    public void setTtfbMs(int ttfbMs) {
        this.ttfbMs = ttfbMs;
    }

    public int getInteractiveMs() {
        return interactiveMs;
    }

    public void setInteractiveMs(int interactiveMs) {
        this.interactiveMs = interactiveMs;
    }

    public int getDomReadyMs() {
        return domReadyMs;
    }

    public void setDomReadyMs(int domReadyMs) {
        this.domReadyMs = domReadyMs;
    }

    public int getLoadMs() {
        return loadMs;
    }

    public void setLoadMs(int loadMs) {
        this.loadMs = loadMs;
    }

    public int getTransferBytes() {
        return transferBytes;
    }

    public void setTransferBytes(int transferBytes) {
        this.transferBytes = transferBytes;
    }

    /** True when a browser actually reported its timings for this view. */
    public boolean hasClientTiming() {
        return interactiveMs >= 0 || domReadyMs >= 0 || loadMs >= 0;
    }
}

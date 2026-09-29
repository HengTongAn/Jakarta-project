package com.hengtongan.computerstore.core.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hengtongan.computerstore.core.repository.AppSettingsRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The contact-support channels shown in the site footer, stored in
 * {@code app_settings} so an admin can set them at {@code /admin/support}.
 * <p>
 * The channel set is fixed in code (label, icon, ordering) while the destination
 * is operator-supplied. That split is deliberate: the footer markup never has to
 * change to add a channel, and a bad value cannot introduce new markup.
 * <p>
 * A channel with no destination is still rendered, but as an inactive chip. An
 * obviously dead icon is better than a link that sends customers to an account
 * belonging to somebody else. Destinations are restricted to {@code http}/
 * {@code https} so a mis-set value cannot turn the footer into a script
 * injection point.
 */
public class SupportChannelService {

    /**
     * One support channel as the footer sees it.
     * <p>
     * Note for JSP authors: test {@code not empty ch.url} to decide whether the
     * channel is live. Tomcat's EL resolver here does not map a record's
     * {@code isX()} boolean accessor to property {@code x}, so a convenience
     * method here would throw {@code PropertyNotFoundException} at render time.
     *
     * @param key      stable identifier, also the form field name
     * @param label    human-readable channel name
     * @param icon     file name under {@code /assets/images}
     * @param example  example destination shown as a placeholder in the admin form
     * @param url      resolved destination, or {@code null} when unconfigured
     */
    public record Channel(String key, String label, String icon, String example, String url) {
    }

    private record Meta(String key, String label, String icon, String example) {
    }

    private static final List<Meta> META = List.of(
            new Meta("facebook", "Facebook", "facebook.svg", "https://facebook.com/yourpage"),
            new Meta("messenger", "Messenger", "messenger.svg", "https://m.me/yourpage"),
            new Meta("telegram", "Telegram", "telegram.svg", "https://t.me/yourchannel"),
            new Meta("x", "X", "x.svg", "https://x.com/yourhandle"));

    private static final String PREFIX = "support.";
    private static final String SUFFIX = ".url";

    /**
     * The footer is rendered on every page, so the four rows are read once and
     * held briefly. The TTL is only a backstop -- {@link #save} invalidates
     * immediately, so an admin edit is visible on the next page load rather than
     * after a window.
     */
    private static final Cache<String, Map<String, String>> SETTINGS = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    /**
     * All channels in display order, with their destinations resolved.
     * Never {@code null}-url for a channel that has a valid destination; an
     * unconfigured or invalid one comes back as {@code null}.
     */
    public List<Channel> channels() {
        Map<String, String> settings = load();
        List<Channel> channels = new ArrayList<>(META.size());
        for (Meta m : META) {
            channels.add(new Channel(m.key(), m.label(), m.icon(), m.example(),
                    safeUrl(settings.get(PREFIX + m.key() + SUFFIX))));
        }
        return channels;
    }

    /**
     * Validates a destination. Returns {@code null} when the value is
     * acceptable (blank means "clear this channel"), otherwise a message fit to
     * show beside the field.
     */
    public String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "Must start with http:// or https://";
        }
        if (value.length() > 500) {
            return "Must be 500 characters or fewer";
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            return "Contains characters that are not allowed in a URL";
        }
        return null;
    }

    /**
     * Persists one destination per channel. Callers are expected to have run
     * {@link #validate} over the whole set first: this writes verbatim, so a
     * rejected value must never reach it.
     */
    public void save(Map<String, String> urlsByChannelKey) {
        AppSettingsRepository repository = new AppSettingsRepository();
        for (Meta m : META) {
            String raw = urlsByChannelKey.get(m.key());
            repository.save(PREFIX + m.key() + SUFFIX, raw == null ? "" : raw.trim());
        }
        SETTINGS.invalidateAll();
    }

    /** Current destination per channel key, blanks included, for form redisplay. */
    public Map<String, String> currentUrls() {
        Map<String, String> settings = load();
        Map<String, String> urls = new LinkedHashMap<>();
        for (Meta m : META) {
            urls.put(m.key(), settings.getOrDefault(PREFIX + m.key() + SUFFIX, ""));
        }
        return urls;
    }

    private Map<String, String> load() {
        return SETTINGS.get("app_settings",
                k -> new AppSettingsRepository().findAll());
    }

    /**
     * Returns the value only when it is an absolute http(s) URL, else
     * {@code null}. The write path already rejects these, but a row can also be
     * edited straight in the database, and the footer is not the place to
     * discover that.
     */
    private String safeUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (validate(value) != null) {
            return null;
        }
        return value;
    }
}

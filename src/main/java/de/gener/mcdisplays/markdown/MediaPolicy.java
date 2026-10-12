package de.gener.mcdisplays.markdown;

import de.gener.mcdisplays.McDisplaysConfig;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Decides whether an image or video URL may be displayed. Media rendering itself is not implemented yet. */
public final class MediaPolicy {
    private MediaPolicy() {
    }

    public static boolean isAllowed(String url) {
        String host = hostOf(url);
        if (host == null) {
            return false;
        }

        List<? extends String> blocked = null;
        try {
            blocked = McDisplaysConfig.blockedMediaHosts();
        } catch (Throwable notLoaded) {
            // Config not available in early/test environments
        }
        return isHostAllowed(host, blocked);
    }

    public static boolean isHostAllowed(String host, Collection<? extends String> blockedHosts) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (blockedHosts != null) {
            for (String entry : blockedHosts) {
                String blockedHost = entry.trim().toLowerCase(Locale.ROOT);
                if (!blockedHost.isEmpty() && (normalizedHost.equals(blockedHost) || normalizedHost.endsWith("." + blockedHost))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.contains("://") && !trimmed.startsWith("file:/")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    public static String hostOf(String url) {
        String clean = normalizeUrl(url);
        if (clean.isEmpty()) {
            return null;
        }
        try {
            String host = URI.create(clean).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}

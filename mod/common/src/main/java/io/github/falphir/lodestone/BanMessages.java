package io.github.falphir.lodestone;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/**
 * The message a network-banned player sees, from the hub's template. Plain Java, so it's testable
 * without a server. Color codes are decoded by the caller, the same as the whitelist message.
 */
public final class BanMessages {

    /** For a hub too old to send one, or one not heard from yet. Matches the hub's BanService.DEFAULT_MESSAGE. */
    public static final String DEFAULT = "&cYou are banned from this network.\n\n&7Reason: &f{reason}\n&7Expires: &f{expires}";

    private static final DateTimeFormatter EXPIRES = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm z", Locale.ENGLISH);

    private BanMessages() {}

    public static String render(String template, String reason, Date expires) {
        return render(template, reason, expires, ZoneId.systemDefault());
    }

    /** {@code zone} is the server's own: the player reads the expiry as the server's clock shows it. */
    static String render(String template, String reason, Date expires, ZoneId zone) {
        return template
                .replace("{reason}", reason == null || reason.isBlank() ? "No reason given" : reason)
                .replace("{expires}", expires == null ? "Never" : EXPIRES.withZone(zone).format(expires.toInstant()));
    }
}

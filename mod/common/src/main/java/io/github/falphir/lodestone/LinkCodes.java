package io.github.falphir.lodestone;

import java.security.SecureRandom;

/**
 * The code a refused player is shown on the kick screen and types into Discord's {@code /link}.
 * Plain Java, so it's testable without a server.
 */
public final class LinkCodes {

    private static final SecureRandom RANDOM = new SecureRandom();

    private LinkCodes() {}

    /** Six digits, leading zeros kept. Matches the hub's LINK_CODE_REGEX; the hub rate-limits wrong guesses. */
    public static String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Puts the code where the kick message says {@code {code}}, or under it when the message doesn't say. */
    public static String withCode(String message, String code) {
        return message.contains("{code}") ? message.replace("{code}", code) : message + "\n\n" + line(code);
    }

    /** The line added under a message that doesn't place the code itself, vanilla's included. */
    public static String line(String code) {
        return "Link your account: run /link " + code + " in Discord";
    }
}

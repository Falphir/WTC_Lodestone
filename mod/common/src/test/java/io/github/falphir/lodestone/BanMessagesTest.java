package io.github.falphir.lodestone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.Test;

class BanMessagesTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void fillsInTheReasonAndExpiry() {
        Date expires = Date.from(Instant.parse("2026-10-28T14:00:00Z"));
        assertEquals("Banned for x-ray until 28 Oct 2026, 14:00 UTC",
                BanMessages.render("Banned for {reason} until {expires}", "x-ray", expires, UTC));
    }

    @Test
    void saysSoWhenThereIsNoReasonOrExpiry() {
        assertEquals("Reason: No reason given, expires: Never",
                BanMessages.render("Reason: {reason}, expires: {expires}", null, null, UTC));
    }
}

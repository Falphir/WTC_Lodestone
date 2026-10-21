package io.github.falphir.lodestone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinkCodesTest {

    @Test
    void codesAreSixDigitsTheHubAccepts() {
        for (int i = 0; i < 1000; i++) {
            String code = LinkCodes.generate();
            // the hub's LINK_CODE_REGEX; small numbers keep their leading zeros
            assertTrue(code.matches("\\d{6}"), code);
        }
    }

    @Test
    void theCodeGoesWhereTheMessageSaysOrUnderIt() {
        assertEquals("Not whitelisted. Code: 042137", LinkCodes.withCode("Not whitelisted. Code: {code}", "042137"));
        assertEquals("Not whitelisted.\n\nLink your account: run /link 042137 in Discord",
                LinkCodes.withCode("Not whitelisted.", "042137"));
    }
}

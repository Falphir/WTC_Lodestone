package io.github.falphir.hub.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.falphir.hub.service.TokenService;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private final TokenService tokens = new TokenService();

    @Test
    void generatesDifferentTokensEachTime() {
        assertThat(tokens.generateToken()).isNotEqualTo(tokens.generateToken());
    }

    @Test
    void tokenIs43UrlSafeCharacters() {
        assertThat(tokens.generateToken())
                .hasSize(43)
                .matches("[A-Za-z0-9_-]+");
    }

    @Test
    void sameInputAlwaysGivesSameHash() {
        assertThat(tokens.hash("my-token")).isEqualTo(tokens.hash("my-token"));
    }

    @Test
    void hashIs64LowercaseHexCharacters() {
        assertThat(tokens.hash("my-token"))
                .hasSize(64)
                .matches("[0-9a-f]+");
    }

    @Test
    void hashMatchesKnownSha256Value() {
        // Official SHA-256 test value for "abc"
        assertThat(tokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
package io.github.falphir.lodestone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The config parser replaced NeoForge's ModConfigSpec, so the thing most worth pinning is that it
 * still reads the files ModConfigSpec wrote -- servers already have one, with their token in it.
 */
class LodestoneConfigTest {

    private final List<String> problems = new ArrayList<>();

    private LodestoneConfig.Values parse(String file) {
        return LodestoneConfig.parse(file.lines().toList(), problems);
    }

    @Test
    void readsAFileWrittenByTheOldModConfigSpec() {
        var values = parse("""
                #Master switch. If false, the mod does nothing.
                enabled = true
                #If true, events are only logged, never sent to the hub. Useful for testing.
                dryRun = false
                hubUrl = "https://hub.example.com"
                serverId = "genesis"
                token = "s3cret-token"
                syncWhitelist = false
                """);

        assertEquals("https://hub.example.com", values.hubUrl());
        assertEquals("genesis", values.serverId());
        assertEquals("s3cret-token", values.token());
        assertTrue(values.enabled());
        assertFalse(values.dryRun());
        assertFalse(values.syncWhitelist());
        assertEquals(List.of(), problems);
    }

    @Test
    void keepsDefaultsForEverythingTheFileDoesNotMention() {
        var values = parse("serverId = \"genesis\"");

        assertTrue(values.enabled());
        assertTrue(values.syncWhitelist());
        assertEquals("http://127.0.0.1:8080", values.hubUrl());
        assertEquals("", values.token());
        assertEquals(List.of(), problems);
    }

    @Test
    void reportsBadValuesAndFallsBackInsteadOfFailing() {
        var values = parse("""
                enabled = yes
                hubUrl = "hub.example.com"
                serverId = "Not Valid"
                token = "still-read"
                """);

        assertTrue(values.enabled(), "a bad boolean keeps its default");
        assertEquals("http://127.0.0.1:8080", values.hubUrl(), "a URL with no scheme is rejected");
        assertEquals("", values.serverId(), "an id with capitals and spaces is rejected");
        assertEquals("still-read", values.token(), "one bad line must not stop the rest being read");
        assertEquals(3, problems.size(), problems.toString());
    }

    @Test
    void decodesLineBreaksInTheWhitelistMessage() {
        var values = parse("whitelistMessage = \"Not whitelisted yet.\\nApply at discord.gg/wtc\"");

        assertEquals("Not whitelisted yet.\nApply at discord.gg/wtc", values.whitelistMessage());
        assertEquals("", parse("serverId = genesis").whitelistMessage(), "empty means: keep vanilla's message");
        assertEquals(List.of(), problems);
    }

    @Test
    void readsValuesWhetherOrNotTheyAreQuoted() {
        assertEquals("genesis", parse("serverId = genesis").serverId());
        assertEquals("genesis", parse("serverId = 'genesis'").serverId());
        assertEquals("genesis", parse("serverId=\"genesis\"").serverId());
    }
}

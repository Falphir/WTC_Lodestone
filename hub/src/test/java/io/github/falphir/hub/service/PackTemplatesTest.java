package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Against the setting names of PufferPanel's stock Minecraft templates, as the panel in use has them. */
class PackTemplatesTest {

    private static final Map<String, Set<String>> PANEL = Map.of(
            "minecraft-neoforge", Set.of("eula", "ip", "javaversion", "memory", "motd", "port", "version"),
            "minecraft-forge", Set.of("eula", "ip", "javaversion", "memory", "motd", "port", "version"),
            "minecraft-forge-legacy", Set.of("eula", "ip", "javaversion", "mc-version", "memory", "motd", "port", "version"),
            "minecraft-fabric", Set.of("eula", "game-version", "ip", "javaversion", "memory", "motd", "port"));

    private static PackTemplates.Suggestion suggest(String mc, String loader, String version, Integer ram) {
        return PackTemplates.suggest(new CurseForgeClient.PackManifest(mc, loader, version, ram), PANEL::get);
    }

    @Test
    void neoForgeGetsItsExactVersionJavaAndThePacksMemory() {
        var s = suggest("1.21.1", "neoforge", "21.1.265", 8196);

        assertThat(s.template()).isEqualTo("minecraft-neoforge");
        assertThat(s.values()).containsExactlyInAnyOrderEntriesOf(Map.of("version", "21.1.265", "javaversion", "21", "memory", 8196));
    }

    @Test
    void forgeTakesTheMinecraftVersionInItsVersionAndPicksLegacyBefore117() {
        var modern = suggest("1.20.1", "forge", "47.3.0", null);
        assertThat(modern.template()).isEqualTo("minecraft-forge");
        // the template has no mc-version setting, so that one is left out
        assertThat(modern.values()).containsExactlyInAnyOrderEntriesOf(Map.of("version", "1.20.1-47.3.0", "javaversion", "17"));

        var legacy = suggest("1.16.5", "forge", "36.2.39", null);
        assertThat(legacy.template()).isEqualTo("minecraft-forge-legacy");
        assertThat(legacy.values()).containsEntry("version", "1.16.5-36.2.39").containsEntry("mc-version", "1.16.5")
                .containsEntry("javaversion", "8");
    }

    @Test
    void fabricOnlyHasAMinecraftVersionToSet() {
        var s = suggest("1.20.1", "fabric", "0.16.9", null);

        assertThat(s.template()).isEqualTo("minecraft-fabric");
        assertThat(s.values()).containsExactlyInAnyOrderEntriesOf(Map.of("game-version", "1.20.1", "javaversion", "17"));
    }

    @Test
    void aLoaderWithoutATemplateGetsNoneButKeepsWhatItFound() {
        var quilt = suggest("1.20.1", "quilt", "0.26.0", null);
        assertThat(quilt.template()).isNull();
        assertThat(quilt.values()).isEmpty();
        assertThat(quilt.loaderVersion()).isEqualTo("0.26.0");
        assertThat(quilt.minecraftVersion()).isEqualTo("1.20.1");

        // NeoForge, but this panel has no NeoForge template
        var missing = PackTemplates.suggest(new CurseForgeClient.PackManifest("1.21.1", "neoforge", "21.1.265", null), name -> null);
        assertThat(missing.template()).isNull();

        assertThat(PackTemplates.suggest(null, PANEL::get).template()).isNull();
    }

    @Test
    void aPackVersionsNameIsCutDownToItsVersion() {
        assertThat(PackTemplates.versionOf("All the Mods 10-8.4")).isEqualTo("8.4");
        assertThat(PackTemplates.versionOf("Just Create SMP 0.5")).isEqualTo("0.5");
        assertThat(PackTemplates.versionOf("Create New World 4.1.1")).isEqualTo("4.1.1");
        assertThat(PackTemplates.versionOf("ATM10-5.1")).isEqualTo("5.1");
        assertThat(PackTemplates.versionOf("Greg-E v0.6.4a")).isEqualTo("0.6.4a");
        assertThat(PackTemplates.versionOf("Season Two")).isEqualTo("Season Two"); // nothing version-like: kept whole
    }

    @Test
    void javaFollowsTheMinecraftVersionNumberByNumber() {
        assertThat(PackTemplates.javaFor("1.20.4")).isEqualTo("17");
        assertThat(PackTemplates.javaFor("1.20.5")).isEqualTo("21");
        assertThat(PackTemplates.javaFor("1.21.10")).isEqualTo("21");
        assertThat(PackTemplates.javaFor("1.18")).isEqualTo("17");
        assertThat(PackTemplates.javaFor("1.17.1")).isEqualTo("8");
    }
}

package io.github.falphir.hub.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which of PufferPanel's templates a modpack runs on, and what to put in its settings, from what the
 * pack's manifest says (loader, its version, Minecraft version, RAM). Written against the panel's stock
 * Minecraft templates, whose setting names differ per loader. Only settings a template actually has
 * are filled, and a loader without a template here gets none, so the admin picks one by hand.
 */
final class PackTemplates {

    /** The template, or null when none matched; {@code values} are the settings it fills in. */
    record Suggestion(String template, Map<String, Object> values, String loader, String loaderVersion, String minecraftVersion) {}

    private PackTemplates() {}

    /**
     * {@code settingsOf}: a template's name → the setting names it takes, or null when the panel has no
     * such template. Only asked about the one template the pack matches.
     */
    static Suggestion suggest(CurseForgeClient.PackManifest pack, Function<String, Set<String>> settingsOf) {
        if (pack == null) return new Suggestion(null, Map.of(), null, null, null);
        String mc = pack.minecraftVersion();
        Map<String, Object> wanted = new LinkedHashMap<>();
        String template = switch (pack.loader() == null ? "" : pack.loader()) {
            case "neoforge" -> {
                wanted.put("version", pack.loaderVersion());
                yield "minecraft-neoforge";
            }
            case "forge" -> {
                wanted.put("version", mc + "-" + pack.loaderVersion());
                wanted.put("mc-version", mc);
                yield mc != null && compare(mc, "1.17") >= 0 ? "minecraft-forge" : "minecraft-forge-legacy";
            }
            case "fabric" -> {
                wanted.put("game-version", mc);
                yield "minecraft-fabric";
            }
            default -> null;
        };
        Set<String> settings = template == null ? null : settingsOf.apply(template);
        if (settings == null) return new Suggestion(null, Map.of(), pack.loader(), pack.loaderVersion(), mc);
        if (mc != null) wanted.put("javaversion", javaFor(mc));
        if (pack.recommendedRamMb() != null) wanted.put("memory", pack.recommendedRamMb());

        Map<String, Object> values = new LinkedHashMap<>();
        wanted.forEach((key, value) -> {
            if (value != null && settings.contains(key)) values.put(key, value);
        });
        return new Suggestion(template, values, pack.loader(), pack.loaderVersion(), mc);
    }

    /** The Java a Minecraft version needs: 21 from 1.20.5, 17 from 1.18, 8 before that. */
    static String javaFor(String minecraftVersion) {
        if (compare(minecraftVersion, "1.20.5") >= 0) return "21";
        if (compare(minecraftVersion, "1.18") >= 0) return "17";
        return "8";
    }

    /** Compares dotted versions number by number, so 1.20.10 comes after 1.20.5. */
    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int diff = Integer.compare(number(x, i), number(y, i));
            if (diff != 0) return diff;
        }
        return 0;
    }

    private static int number(String[] parts, int i) {
        if (i >= parts.length) return 0;
        String digits = parts[i].replaceAll("\\D.*", ""); // "1-pre1" → 1
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}

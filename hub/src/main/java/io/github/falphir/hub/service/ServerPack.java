package io.github.falphir.hub.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Trims a modpack's server files down to what belongs in a server's folder. Server packs are built for
 * someone unzipping them by hand: wrapped in a folder of their own, with start scripts, loader
 * installers and readmes next to the pack. Here the template installs the loader and runs the server,
 * so only the pack's own folders (mods, config, kubejs...) are kept, moved up to the server's root.
 */
final class ServerPack {

    /** Folders that are runtime output or the loader's own install, never part of a pack. */
    private static final Set<String> DROPPED_FOLDERS = Set.of("libraries", "logs", "crash-reports", "world");

    /**
     * The only files kept from the pack's root; scripts, installers, eula.txt, server.properties (the
     * template writes its own) and readmes all go. ponytail: a fixed list; widen it when a pack turns
     * up with a root file its server can't start without.
     */
    private static final Set<String> KEPT_ROOT_FILES = Set.of("server-icon.png");

    private ServerPack() {}

    /** Copies the entries of {@code zipIn} worth keeping into {@code zipOut}; returns how many files that was. */
    static int filter(Path zipIn, Path zipOut) throws IOException {
        String root = rootOf(zipIn);
        int kept = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zipIn));
                ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zipOut))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                String path = normalized(entry.getName());
                if (entry.isDirectory() || path == null || !path.startsWith(root)) continue;
                path = path.substring(root.length());
                if (!keep(path)) continue;
                out.putNextEntry(new ZipEntry(path));
                in.transferTo(out);
                out.closeEntry();
                kept++;
            }
        }
        return kept;
    }

    static boolean keep(String path) {
        int slash = path.indexOf('/');
        if (slash < 0) return KEPT_ROOT_FILES.contains(path);
        if (DROPPED_FOLDERS.contains(path.substring(0, slash))) return false;
        // the hub writes these itself before the pack lands, and the panel won't unpack over an existing file
        if (path.equals(ServerProvisioningService.CONFIG_PATH)) return false;
        return !(path.startsWith("mods/wtc_lodestone-") && path.endsWith(".jar"));
    }

    /**
     * Where the pack starts inside the zip: the folder holding its mods/ folder, so a wrapper folder
     * ("Server-Files-1.2/") is looked through. A pack with no mods/ folder is taken as it is.
     */
    static String rootOf(Path zip) throws IOException {
        String best = null;
        try (InputStream file = Files.newInputStream(zip); ZipInputStream in = new ZipInputStream(file)) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                String path = normalized(entry.getName());
                if (path == null) continue;
                String candidate;
                if (path.startsWith("mods/")) {
                    candidate = "";
                } else {
                    int at = path.indexOf("/mods/");
                    if (at < 0) continue;
                    candidate = path.substring(0, at + 1);
                }
                if (best == null || candidate.length() < best.length()) best = candidate;
            }
        }
        return best == null ? "" : best;
    }

    /** Forward slashes, and null for anything that tries to climb out of the folder it's unpacked into. */
    private static String normalized(String name) {
        String path = name.replace('\\', '/');
        if (path.startsWith("/") || path.contains(":")) return null;
        for (String part : path.split("/")) {
            if (part.equals("..")) return null;
        }
        return path;
    }
}

package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerPackTest {

    @TempDir
    Path dir;

    @Test
    void keepsThePacksFoldersAtTheRootAndDropsWhatsForAPersonUnzippingIt() throws IOException {
        var entries = new LinkedHashMap<String, String>();
        entries.put("ATM10-Server-5.1/", "");
        entries.put("ATM10-Server-5.1/mods/create.jar", "jar");
        entries.put("ATM10-Server-5.1/config/create-common.toml", "cfg");
        entries.put("ATM10-Server-5.1/kubejs/server_scripts/recipes.js", "js");
        entries.put("ATM10-Server-5.1/startserver.sh", "#!/bin/sh");
        entries.put("ATM10-Server-5.1/startserver.bat", "@echo off");
        entries.put("ATM10-Server-5.1/neoforge-21.1.77-installer.jar", "installer");
        entries.put("ATM10-Server-5.1/eula.txt", "eula=false");
        entries.put("ATM10-Server-5.1/server.properties", "server-port=25565");
        entries.put("ATM10-Server-5.1/server-icon.png", "png");
        entries.put("ATM10-Server-5.1/libraries/net/neoforged/x.jar", "lib");

        assertThat(filter(entries)).containsExactlyInAnyOrder(
                "mods/create.jar", "config/create-common.toml", "kubejs/server_scripts/recipes.js", "server-icon.png");
    }

    @Test
    void aPackWithoutAWrapperFolderIsTakenAsItIs() throws IOException {
        assertThat(filter(Map.of("mods/a.jar", "jar", "defaultconfigs/b.toml", "cfg", "run.sh", "x")))
                .containsExactlyInAnyOrder("mods/a.jar", "defaultconfigs/b.toml");
    }

    @Test
    void neverOverwritesWhatTheHubWroteItself() throws IOException {
        // the panel won't unpack over an existing file, which would stop the whole unpack
        assertThat(filter(Map.of("mods/a.jar", "jar", "mods/wtc_lodestone-neoforge-1.21.1-0.9.0.jar", "old",
                "config/wtc_lodestone-common.toml", "token = \"stale\"")))
                .containsExactly("mods/a.jar");
    }

    @Test
    void entriesThatClimbOutOfTheFolderAreDropped() throws IOException {
        assertThat(filter(Map.of("mods/a.jar", "jar", "mods/../../etc/passwd", "x", "/abs/mods/b.jar", "x")))
                .containsExactly("mods/a.jar");
    }

    private List<String> filter(Map<String, String> entries) throws IOException {
        Path in = dir.resolve("in.zip");
        Path out = dir.resolve("out.zip");
        try (OutputStream file = Files.newOutputStream(in); ZipOutputStream zip = new ZipOutputStream(file)) {
            for (var e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        int kept = ServerPack.filter(in, out);
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(out.toFile())) {
            zip.stream().forEach(e -> names.add(e.getName()));
        }
        assertThat(kept).isEqualTo(names.size());
        return names;
    }
}

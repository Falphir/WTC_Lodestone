package io.github.falphir.hub.service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import io.github.falphir.hub.config.LodestoneProperties;

/**
 * The hub's client for CurseForge's API, for picking a modpack when a server is created from the
 * dashboard. The key stays on the hub: the dashboard searches through the hub, never CurseForge itself.
 * <p>
 * Optional: with no API key configured, {@link #isConfigured()} is false and nothing here calls out.
 */
@Component
public class CurseForgeClient {

    static final String API = "https://api.curseforge.com";
    private static final int MINECRAFT = 432;
    /** CurseForge's "Modpacks" class for Minecraft. */
    private static final int MODPACKS = 4471;

    /** Why a call to CurseForge failed, worded for an admin. Never carries the API key. */
    public static class CurseForgeException extends RuntimeException {
        public CurseForgeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** {@code downloadable} false: the author only allows downloads through CurseForge's own app. */
    public record Modpack(int id, String name, String summary, String logoUrl, String websiteUrl, boolean downloadable) {}

    /** One version of a pack that has server files ({@code serverPackFileId}). */
    public record PackFile(int fileId, int serverPackFileId, String displayName, List<String> gameVersions, String fileDate) {}

    private final LodestoneProperties.CurseForge config;
    private final RestClient http;

    @Autowired
    public CurseForgeClient(LodestoneProperties properties) {
        this(properties.curseforge(), RestClient.builder().requestFactory(factory()));
    }

    /** Takes the builder so tests can bind it to a mock server. */
    CurseForgeClient(LodestoneProperties.CurseForge config, RestClient.Builder builder) {
        this.config = config;
        this.http = builder.build();
    }

    /**
     * Follows redirects, since a file's download URL may answer with one. The read timeout is long
     * because the same client streams server packs, which can be hundreds of MB.
     */
    private static JdkClientHttpRequestFactory factory() {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofMinutes(30));
        return factory;
    }

    public boolean isConfigured() {
        return config != null && config.isConfigured();
    }

    /** Minecraft modpacks matching {@code query}, most popular first. */
    public List<Modpack> searchModpacks(String query) {
        Map<?, ?> answer = api("/v1/mods/search?gameId={game}&classId={modpacks}&searchFilter={query}&sortField=2&sortOrder=desc&pageSize=20",
                MINECRAFT, MODPACKS, query == null ? "" : query.trim());
        return list(answer.get("data")).stream().map(CurseForgeClient::modpack).toList();
    }

    public Modpack modpack(int modId) {
        return modpack(api("/v1/mods/{id}", modId).get("data"));
    }

    /** The pack's versions that come with server files, newest first. */
    public List<PackFile> serverFiles(int modId) {
        Map<?, ?> answer = api("/v1/mods/{id}/files?pageSize=50", modId);
        return list(answer.get("data")).stream()
                .map(CurseForgeClient::packFile)
                .filter(f -> f != null)
                .sorted(Comparator.comparing(PackFile::fileDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public PackFile file(int modId, int fileId) {
        PackFile file = packFile(api("/v1/mods/{id}/files/{file}", modId, fileId).get("data"));
        if (file == null) throw new CurseForgeException("That version of the pack has no server files", null);
        return file;
    }

    /**
     * Downloads a file of a pack (e.g. its server pack) to {@code target}, streamed rather than held in
     * memory. {@code progress} hears (bytes so far, total) as it goes; total is -1 if CurseForge didn't say.
     */
    public void download(int modId, int fileId, Path target, TransferProgress progress) {
        Object url = api("/v1/mods/{id}/files/{file}/download-url", modId, fileId).get("data");
        if (!(url instanceof String link) || link.isBlank()) {
            throw new CurseForgeException("CurseForge has no download for that file; its author only allows downloads through the CurseForge app", null);
        }
        try {
            // CurseForge's CDN links carry the file name as is, spaces included
            http.get().uri(URI.create(link.replace(" ", "%20"))).exchange((request, response) -> {
                if (response.getStatusCode().isError()) {
                    throw new CurseForgeException("The download answered HTTP " + response.getStatusCode().value(), null);
                }
                long total = response.getHeaders().getContentLength();
                try (InputStream body = new TransferProgress.Counting(response.getBody(), total, progress)) {
                    Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            });
        } catch (RestClientException e) {
            throw new CurseForgeException("Couldn't download the server files: " + e.getMessage(), e);
        }
    }

    /**
     * What a pack version runs on, from the {@code manifest.json} in its own (client) file: CurseForge's
     * API only says "NeoForge" and "1.21.1", never which NeoForge. The manifest is the zip's first entry,
     * so only the start of the file is read, however big the rest is. Null when it can't be had (a pack
     * only the CurseForge app may download, or one without a manifest), since a guess is never needed.
     */
    public PackManifest manifest(int modId, int fileId) {
        Object url;
        try {
            url = api("/v1/mods/{id}/files/{file}/download-url", modId, fileId).get("data");
        } catch (CurseForgeException e) {
            return null;
        }
        if (!(url instanceof String link) || link.isBlank()) return null;
        try {
            return http.get().uri(URI.create(link.replace(" ", "%20"))).exchange((request, response) -> {
                if (response.getStatusCode().isError()) return null;
                // closing early is what stops the download: the rest of the zip is never read
                try (ZipInputStream zip = new ZipInputStream(response.getBody())) {
                    ZipEntry entry;
                    for (int seen = 0; seen < MANIFEST_WITHIN && (entry = zip.getNextEntry()) != null; seen++) {
                        if (entry.getName().equals("manifest.json")) {
                            return PackManifest.parse(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    }
                    return null;
                }
            });
        } catch (RestClientException | IllegalArgumentException e) {
            return null;
        }
    }

    /** How many entries in to look for manifest.json before deciding a pack has none. */
    private static final int MANIFEST_WITHIN = 5;

    /** {@code loader}: neoforge, forge, fabric...; {@code recommendedRamMb} null when the pack doesn't say. */
    public record PackManifest(String minecraftVersion, String loader, String loaderVersion, Integer recommendedRamMb) {

        static PackManifest parse(String json) {
            Map<String, Object> manifest = JsonParserFactory.getJsonParser().parseMap(json);
            Map<?, ?> minecraft = manifest.get("minecraft") instanceof Map<?, ?> m ? m : Map.of();
            // the one marked primary, else the first
            Map<?, ?> primary = Map.of();
            for (Object entry : list(minecraft.get("modLoaders"))) {
                if (!(entry instanceof Map<?, ?> loader)) continue;
                if (primary.isEmpty() || Boolean.TRUE.equals(loader.get("primary"))) primary = loader;
                if (Boolean.TRUE.equals(loader.get("primary"))) break;
            }
            // "neoforge-21.1.265" → neoforge, 21.1.265
            String id = string(primary.get("id"));
            int dash = id == null ? -1 : id.indexOf('-');
            return new PackManifest(string(minecraft.get("version")),
                    dash > 0 ? id.substring(0, dash).toLowerCase() : id,
                    dash > 0 ? id.substring(dash + 1) : null,
                    minecraft.get("recommendedRam") instanceof Number ram ? ram.intValue() : null);
        }
    }

    private Map<?, ?> api(String uri, Object... uriVariables) {
        if (!isConfigured()) throw new CurseForgeException("CurseForge isn't configured", null);
        try {
            Map<?, ?> answer = http.get().uri(API + uri, uriVariables)
                    .header("x-api-key", config.apiKey())
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);
            if (answer == null) throw new CurseForgeException("CurseForge gave an empty answer", null);
            return answer;
        } catch (HttpClientErrorException.Forbidden e) {
            throw new CurseForgeException("CurseForge refused the request (HTTP 403): the API key is wrong, or the pack's "
                    + "author only allows downloads through the CurseForge app", e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new CurseForgeException("CurseForge has no such pack or file", e);
        } catch (RestClientResponseException e) {
            throw new CurseForgeException("CurseForge answered HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new CurseForgeException("Couldn't reach CurseForge", e);
        }
    }

    private static Modpack modpack(Object raw) {
        Map<?, ?> m = raw instanceof Map<?, ?> map ? map : Map.of();
        Map<?, ?> logo = m.get("logo") instanceof Map<?, ?> l ? l : Map.of();
        Map<?, ?> links = m.get("links") instanceof Map<?, ?> l ? l : Map.of();
        return new Modpack(m.get("id") instanceof Number id ? id.intValue() : 0, string(m.get("name")), string(m.get("summary")),
                string(logo.get("thumbnailUrl")), string(links.get("websiteUrl")),
                !Boolean.FALSE.equals(m.get("allowModDistribution")));
    }

    /** Null for a file without server files, which is no use to a server. */
    private static PackFile packFile(Object raw) {
        if (!(raw instanceof Map<?, ?> f) || !(f.get("serverPackFileId") instanceof Number serverPack) || !(f.get("id") instanceof Number id)) {
            return null;
        }
        List<String> versions = list(f.get("gameVersions")).stream().map(String::valueOf).toList();
        // some packs name a version after its file ("Just Create SMP 0.5.zip")
        String name = string(f.get("displayName"));
        if (name != null) name = name.replaceFirst("(?i)\\.zip$", "");
        return new PackFile(id.intValue(), serverPack.intValue(), name, versions, string(f.get("fileDate")));
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> l ? l : List.of();
    }

    private static String string(Object value) {
        return value instanceof String s ? s : null;
    }
}

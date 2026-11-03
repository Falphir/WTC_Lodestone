package io.github.falphir.hub.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Fetches the WTC Lodestone mod jar from the newest GitHub release, so a server created from the
 * dashboard has it in mods/ from its first start. The repo is public, so no token is involved.
 */
@Component
public class ModRelease {

    public record Jar(String fileName, byte[] bytes) {}

    private final String repo;
    private final RestClient http;

    @Autowired
    public ModRelease(@Value("${lodestone.mod-release-repo:Falphir/WTC_Lodestone}") String repo) {
        // a release asset's download URL redirects to GitHub's file storage, and the JDK client
        // doesn't follow redirects unless told to
        this(repo, RestClient.builder().requestFactory(redirectingFactory()));
    }

    /** Takes the builder so tests can bind it to a mock server. */
    ModRelease(String repo, RestClient.Builder builder) {
        this.repo = repo;
        this.http = builder.build();
    }

    private static JdkClientHttpRequestFactory redirectingFactory() {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        return factory;
    }

    /** The NeoForge jar of the latest release. Throws with an admin-readable message when it can't. */
    public Jar latestNeoForgeJar() {
        try {
            // Spliced in rather than passed as {repo}: a URI variable gets its "/" encoded as %2F,
            // which GitHub answers with a 404. The repo is the hub's own config, not user input.
            Map<?, ?> release = http.get().uri(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(Map.class);
            List<?> assets = release == null || !(release.get("assets") instanceof List<?> list) ? List.of() : list;
            for (Object asset : assets) {
                if (asset instanceof Map<?, ?> a && a.get("name") instanceof String name
                        && name.startsWith("wtc_lodestone-neoforge-") && name.endsWith(".jar")
                        && a.get("browser_download_url") instanceof String url) {
                    byte[] bytes = http.get().uri(URI.create(url)).retrieve().body(byte[].class);
                    if (bytes == null || bytes.length == 0) throw new IllegalStateException("the download was empty");
                    return new Jar(name, bytes);
                }
            }
            throw new IllegalStateException("the latest release of " + repo + " has no NeoForge jar");
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("GitHub answered HTTP " + e.getStatusCode().value() + " for " + repo + "'s latest release", e);
        } catch (RestClientException e) {
            throw new IllegalStateException("couldn't reach GitHub for " + repo + "'s latest release", e);
        }
    }
}

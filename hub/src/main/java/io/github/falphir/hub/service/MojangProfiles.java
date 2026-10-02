package io.github.falphir.hub.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Resolves Minecraft usernames to account UUIDs through Mojang's public API. */
@Component
public class MojangProfiles {

    private final RestClient http;

    public MojangProfiles() {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl("https://api.mojang.com").requestFactory(factory).build();
    }

    /** Finds the account with this name (case-insensitive). Empty if there is none. */
    public Optional<Profile> lookup(String name) {
        try {
            MojangProfile body = http.get().uri("/users/profiles/minecraft/{name}", name)
                    .retrieve()
                    .body(MojangProfile.class);
            return Optional.ofNullable(body).map(p -> new Profile(dashed(p.id()), p.name()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Couldn't reach Mojang to look up '" + name + "'", e);
        }
    }

    /** Mojang returns UUIDs without dashes; whitelist.json and the game use the dashed form. */
    static String dashed(String undashed) {
        return UUID.fromString(undashed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5")).toString();
    }

    public record Profile(String uuid, String name) {}

    public record MojangProfile(String id, String name) {}
}

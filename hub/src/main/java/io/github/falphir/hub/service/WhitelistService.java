package io.github.falphir.hub.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.falphir.hub.entity.WhitelistedPlayer;
import io.github.falphir.hub.repository.WhitelistedPlayerRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WhitelistService {

    private final WhitelistedPlayerRepository repository;
    private final MojangProfiles mojang;
    private final TokenService hashing;
    private final ApplicationEventPublisher events;

    public WhitelistService(WhitelistedPlayerRepository repository, MojangProfiles mojang, TokenService hashing,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.mojang = mojang;
        this.hashing = hashing;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<WhitelistedPlayer> list() {
        return repository.findAllByOrderByNameAsc();
    }

    /**
     * Identifies the current contents of the whitelist: a hash of the sorted UUIDs. Servers report the version they
     * applied, so the dashboard can tell which ones are behind. Names are left out, they don't affect who can join.
     */
    @Transactional(readOnly = true)
    public String version() {
        return version(list());
    }

    public String version(List<WhitelistedPlayer> players) {
        return hashing.hash(players.stream().map(WhitelistedPlayer::getUuid).sorted().collect(Collectors.joining(",")));
    }

    /** Whitelists the Minecraft account with this name, looking up its UUID through Mojang. */
    @Transactional
    public WhitelistedPlayer add(String name, String addedBy) {
        MojangProfiles.Profile profile = mojang.lookup(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No Minecraft account named '" + name + "'"));
        if (repository.existsById(profile.uuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "'" + profile.name() + "' is already whitelisted");
        }
        WhitelistedPlayer player = repository.save(new WhitelistedPlayer(profile.uuid(), profile.name(), addedBy));
        events.publishEvent(HubEvent.whitelist());
        return player;
    }

    /** Adds entries from an existing whitelist.json. Already-whitelisted players are skipped. Returns how many were added. */
    @Transactional
    public int importEntries(List<Entry> entries, String addedBy) {
        Set<String> seen = new HashSet<>();
        int added = 0;
        for (Entry entry : entries) {
            String uuid = UUID.fromString(entry.uuid()).toString();
            if (!seen.add(uuid) || repository.existsById(uuid)) continue;
            repository.save(new WhitelistedPlayer(uuid, entry.name(), addedBy));
            added++;
        }
        if (added > 0) events.publishEvent(HubEvent.whitelist());
        return added;
    }

    @Transactional
    public void remove(String uuid) {
        if (!repository.existsById(uuid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Player is not whitelisted");
        }
        repository.deleteById(uuid);
        events.publishEvent(HubEvent.whitelist());
    }

    public record Entry(String uuid, String name) {}
}

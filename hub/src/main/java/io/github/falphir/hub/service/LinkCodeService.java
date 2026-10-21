package io.github.falphir.hub.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Codes a game server showed a refused player on its kick screen, waiting for that player to run
 * {@code /link <code>} in Discord. Login already proved who they are (online mode authenticates
 * with Mojang before the whitelist check), so a claimed code is proof of owning that account.
 * <p>
 * Codes are six digits, so only a million exist: each Discord account gets {@value #MAX_WRONG} wrong
 * guesses per {@link #TTL} -- the life of a code -- which keeps guessing someone else's live code hopeless.
 * <p>
 * ponytail: in memory, so a hub restart drops pending codes and the player just reconnects for a
 * fresh one. Move to a table if codes ever need to outlive a restart.
 */
@Service
public class LinkCodeService {

    static final Duration TTL = Duration.ofMinutes(5);
    static final int MAX_WRONG = 5;

    public record Pending(String uuid, String name, Instant expiresAt) {}

    /** Wrong codes from one Discord account since {@code since}. */
    private record Misses(int count, Instant since) {}

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, Misses> misses = new ConcurrentHashMap<>();

    public void offer(String code, String uuid, String name) {
        prune();
        pending.put(code, new Pending(uuid, name, Instant.now().plus(TTL)));
    }

    /** Single use: a claimed code is gone, whether or not linking then succeeds. 429 once this account has guessed wrong too often. */
    public Optional<Pending> claim(String code, String discordId) {
        prune();
        Misses wrong = misses.get(discordId);
        if (wrong != null && wrong.count() >= MAX_WRONG) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong link codes, try again in a few minutes");
        }
        Pending found = pending.remove(code);
        if (found == null) {
            misses.merge(discordId, new Misses(1, Instant.now()), (old, one) -> new Misses(old.count() + 1, old.since()));
        }
        return Optional.ofNullable(found);
    }

    private void prune() {
        Instant now = Instant.now();
        pending.values().removeIf(p -> p.expiresAt().isBefore(now));
        misses.values().removeIf(m -> m.since().plus(TTL).isBefore(now));
    }
}

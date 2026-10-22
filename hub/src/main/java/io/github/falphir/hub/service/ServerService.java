package io.github.falphir.hub.service;

import io.github.falphir.hub.entity.GameServer;
import io.github.falphir.hub.entity.ServerHeartbeat;
import io.github.falphir.hub.repository.GameServerRepository;
import io.github.falphir.hub.repository.ServerHeartbeatRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ServerService {

    /** A server that missed this many heartbeats in a row counts as offline. */
    private static final int MISSED_HEARTBEATS_OFFLINE = 3;

    /** Below this TPS the server is visibly lagging for players. */
    public static final double LAGGING_BELOW_TPS = 15.0;

    private final GameServerRepository repository;
    private final TokenService tokens;
    private final ServerHeartbeatRepository heartbeats;
    private final ApplicationEventPublisher events;
    private final PufferPanelClient panel;
    private final Duration heartbeatInterval;

    /**
     * Who was on each server as of its last heartbeat. Deliberately not a table: presence is only
     * ever interesting live, one heartbeat refills it after a hub restart, and keeping it out of the
     * database avoids a write per server per minute plus a history nobody asked for.
     * <p>
     * ponytail: in-memory, so it is per-process -- if the hub ever runs as more than one instance,
     * this becomes a column on servers (or Redis) rather than a map.
     */
    private final Map<String, List<OnlinePlayer>> online = new ConcurrentHashMap<>();

    @PersistenceContext
    private EntityManager entityManager;

    public ServerService(GameServerRepository repository, TokenService tokens, ServerHeartbeatRepository heartbeats,
            ApplicationEventPublisher events, PufferPanelClient panel,
            @Value("${lodestone.heartbeat-interval-seconds:60}") int heartbeatIntervalSeconds) {
        this.panel = panel;
        this.repository = repository;
        this.tokens = tokens;
        this.heartbeats = heartbeats;
        this.events = events;
        this.heartbeatInterval = Duration.ofSeconds(heartbeatIntervalSeconds);
    }

    /** How often servers send a heartbeat. The mod is told this on check-in, the dashboard reads it from /api/info. */
    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    public Duration offlineAfter() {
        return heartbeatInterval.multipliedBy(MISSED_HEARTBEATS_OFFLINE);
    }

    /**
     * Registers a new server, with as much of its public listing as is known already ({@code
     * listing} may be null, and its id/name are ignored in favour of the arguments). The plain
     * token is returned once and never stored.
     */
    @Transactional
    public RegisteredServer register(String id, String name, Patch listing) {
        if (repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server '" + id + "' already exists");
        }
        String token = tokens.generateToken();
        GameServer server = repository.save(new GameServer(id, name, tokens.hash(token)));
        if (listing != null) applyListing(listing, server);
        events.publishEvent(HubEvent.servers(id));
        return new RegisteredServer(id, name, token);
    }

    /** Finds the enabled server that owns this token, if any. */
    @Transactional(readOnly = true)
    public Optional<GameServer> authenticate(String token) {
        return repository.findByTokenHash(tokens.hash(token))
                .filter(GameServer::isEnabled);
    }

    /** Check-in from the mod: marks the server seen and stores what it reported about itself. */
    @Transactional
    public GameServer checkIn(String id, String modVersion, String minecraftVersion, String loaderVersion,
            Boolean syncWhitelist, Boolean enforceWhitelist, Boolean syncBans) {
        GameServer server = find(id);
        server.markSeen();
        server.reportSetup(modVersion, minecraftVersion, loaderVersion, syncWhitelist, enforceWhitelist, syncBans);
        events.publishEvent(HubEvent.servers(id));
        return server;
    }

    /** Records a periodic heartbeat: marks the server seen, appends a row to its stats history and replaces who is on. */
    @Transactional
    public void recordHeartbeat(String id, int playerCount, int maxPlayers, double tps, double msptAvg,
            int memoryUsedMb, int memoryMaxMb, List<OnlinePlayer> players) {
        find(id).markSeen();
        heartbeats.save(new ServerHeartbeat(id, playerCount, maxPlayers, tps, msptAvg, memoryUsedMb, memoryMaxMb));
        online.put(id, players == null ? List.of() : List.copyOf(players));
        events.publishEvent(HubEvent.servers(id));
    }

    /** Who is on this server, empty once it has been quiet long enough to count as offline. */
    public List<OnlinePlayer> onlinePlayers(GameServer server) {
        if (server.getLastSeen() == null || server.getLastSeen().isBefore(Instant.now().minus(offlineAfter()))) {
            return List.of();
        }
        return online.getOrDefault(server.getId(), List.of());
    }

    public record OnlinePlayer(String uuid, String name) {}

    /**
     * Applies the non-null fields of {@code patch} to a server. A new id is a special case: it
     * changes the row's own key, and the mod's config needs the same new id to keep matching.
     */
    @Transactional
    public GameServer edit(String id, Patch patch) {
        GameServer server = find(id);
        String newId = patch.id();

        if (newId != null && !newId.equals(id)) {
            if (repository.existsById(newId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Server '" + newId + "' already exists");
            }
            repository.changeId(id, newId);
            // changeId is a bulk update, so the copy loaded above still answers to the old id.
            // Dropping it is what makes the reload below see the row as it now is.
            entityManager.clear();
            server = find(newId);
            // presence is keyed by server id, so it has to move with the row or the renamed
            // server looks empty until its next heartbeat
            List<OnlinePlayer> wasOnline = online.remove(id);
            if (wasOnline != null) online.put(newId, wasOnline);
        }

        applyListing(patch, server);
        events.publishEvent(HubEvent.servers(server.getId()));
        return server;
    }

    /** Everything a patch can change except the id, which moves the row and is handled on its own. */
    private void applyListing(Patch patch, GameServer server) {
        if (patch.name() != null) server.setName(patch.name());
        if (patch.publicAddress() != null) server.setPublicAddress(patch.publicAddress());
        if (patch.modpack() != null) server.setModpack(patch.modpack());
        if (patch.modpackUrl() != null) server.setModpackUrl(patch.modpackUrl());
        if (patch.modpackVersion() != null) server.setModpackVersion(patch.modpackVersion());
        if (patch.launcher() != null) server.setLauncher(patch.launcher());
        if (patch.iconUrl() != null) server.setIconUrl(patch.iconUrl());
        if (patch.published() != null) server.setPublished(patch.published());
        if (patch.pufferpanelId() != null) {
            if (patch.pufferpanelId().isBlank()) server.setPufferpanelId(null);
            else linkPanelServer(server, patch.pufferpanelId());
        }
    }

    /**
     * Links this hub server to a PufferPanel server, which must exist on the panel and not already be
     * another hub server's. Asks the panel, so linking needs PufferPanel configured and reachable.
     */
    private void linkPanelServer(GameServer server, String pufferpanelId) {
        if (pufferpanelId.equals(server.getPufferpanelId())) return;
        if (!panel.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PufferPanel isn't configured");
        }
        boolean exists;
        try {
            exists = panel.listServers().stream().anyMatch(s -> pufferpanelId.equals(s.id()));
        } catch (PufferPanelClient.PanelException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No PufferPanel server '" + pufferpanelId + "'");
        }
        repository.findByPufferpanelId(pufferpanelId)
                .filter(other -> !other.getId().equals(server.getId()))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "PufferPanel server '" + pufferpanelId + "' is already linked to '" + other.getId() + "'");
                });
        server.setPufferpanelId(pufferpanelId);
    }

    /** PufferPanel server id → the hub server linked to it, for every linked server. */
    @Transactional(readOnly = true)
    public Map<String, String> panelLinks() {
        return repository.findByPufferpanelIdNotNull().stream()
                .collect(Collectors.toMap(GameServer::getPufferpanelId, GameServer::getId));
    }

    /** Only non-null fields are changed, the same merge the Discord config patch uses. {@code pufferpanelId} "" unlinks. */
    public record Patch(String id, String name, String publicAddress, String modpack, String modpackUrl,
            String modpackVersion, String launcher, String iconUrl, Boolean published, String pufferpanelId) {}

    /** Issues a new token for a server, invalidating the old one. Returned once and never stored. */
    @Transactional
    public RegisteredServer resetToken(String id) {
        GameServer server = find(id);
        String token = tokens.generateToken();
        server.rotateToken(tokens.hash(token));
        events.publishEvent(HubEvent.servers(id));
        return new RegisteredServer(server.getId(), server.getName(), token);
    }

    /** The mod applied this version of the network whitelist. */
    @Transactional
    public void whitelistApplied(String id, String version) {
        find(id).whitelistApplied(version);
        events.publishEvent(HubEvent.servers(id));
    }

    /** The mod applied this version of the network ban list. */
    @Transactional
    public void bansApplied(String id, String version) {
        find(id).bansApplied(version);
        events.publishEvent(HubEvent.servers(id));
    }

    /** Returns a server's heartbeats recorded after {@code since}, most recent first. */
    @Transactional(readOnly = true)
    public List<ServerHeartbeat> heartbeatHistory(String id, Instant since) {
        return heartbeats.findByServerIdAndRecordedAtAfterOrderByRecordedAtDesc(id, since);
    }

    /** Returns a server's most recent heartbeat, if it has sent any. */
    @Transactional(readOnly = true)
    public Optional<ServerHeartbeat> latestHeartbeat(String id) {
        return heartbeats.findFirstByServerIdOrderByRecordedAtDesc(id);
    }

    /** Returns all registered servers. */
    @Transactional(readOnly = true)
    public List<GameServer> listServers() {
        return repository.findAll();
    }

    /** Removes a server and its heartbeat history. It stops being able to authenticate immediately. */
    @Transactional
    public void remove(String id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown server '" + id + "'");
        }
        heartbeats.deleteByServerId(id); // the FK from server_heartbeats has no ON DELETE CASCADE
        online.remove(id);
        repository.deleteById(id);
        events.publishEvent(HubEvent.servers(id));
    }

    /** The one place that decides whether a server is up; the dashboard shows what this says. */
    public ServerStatus status(GameServer server, ServerHeartbeat latest) {
        if (!server.isEnabled()) return ServerStatus.DISABLED;
        if (server.getLastSeen() == null) return ServerStatus.NEVER;
        if (server.getLastSeen().isBefore(Instant.now().minus(offlineAfter()))) return ServerStatus.OFFLINE;
        if (latest != null && latest.getTps() < LAGGING_BELOW_TPS) return ServerStatus.LAGGING;
        return ServerStatus.ONLINE;
    }

    private GameServer find(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown server '" + id + "'"));
    }

    public enum ServerStatus { ONLINE, LAGGING, OFFLINE, NEVER, DISABLED }

    public record RegisteredServer(String id, String name, String token) {}
}

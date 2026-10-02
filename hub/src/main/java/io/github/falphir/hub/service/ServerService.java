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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    private final Duration heartbeatInterval;

    public ServerService(GameServerRepository repository, TokenService tokens, ServerHeartbeatRepository heartbeats,
            ApplicationEventPublisher events,
            @Value("${lodestone.heartbeat-interval-seconds:60}") int heartbeatIntervalSeconds) {
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

    /** Registers a new server. The plain token is returned once and never stored. */
    @Transactional
    public RegisteredServer register(String id, String name) {
        if (repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server '" + id + "' already exists");
        }
        String token = tokens.generateToken();
        repository.save(new GameServer(id, name, tokens.hash(token)));
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
    public GameServer checkIn(String id, String modVersion, String minecraftVersion, String loaderVersion, Boolean syncWhitelist) {
        GameServer server = find(id);
        server.markSeen();
        server.reportSetup(modVersion, minecraftVersion, loaderVersion, syncWhitelist);
        events.publishEvent(HubEvent.servers(id));
        return server;
    }

    /** Records a periodic heartbeat: marks the server seen and appends a row to its stats history. */
    @Transactional
    public void recordHeartbeat(String id, int playerCount, int maxPlayers, double tps, int memoryUsedMb, int memoryMaxMb) {
        find(id).markSeen();
        heartbeats.save(new ServerHeartbeat(id, playerCount, maxPlayers, tps, memoryUsedMb, memoryMaxMb));
        events.publishEvent(HubEvent.servers(id));
    }

    /** The mod applied this version of the network whitelist. */
    @Transactional
    public void whitelistApplied(String id, String version) {
        find(id).whitelistApplied(version);
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

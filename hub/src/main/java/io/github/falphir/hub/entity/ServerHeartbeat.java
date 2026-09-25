package io.github.falphir.hub.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One reported snapshot of a server's stats. Rows are never updated, only inserted. */
@Entity
@Table(name = "server_heartbeats")
public class ServerHeartbeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "server_id", nullable = false, length = 64)
    private String serverId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(nullable = false)
    private double tps;

    @Column(name = "memory_used_mb", nullable = false)
    private int memoryUsedMb;

    @Column(name = "memory_max_mb", nullable = false)
    private int memoryMaxMb;

    protected ServerHeartbeat() {
        // required by JPA
    }

    public ServerHeartbeat(String serverId, int playerCount, int maxPlayers, double tps, int memoryUsedMb, int memoryMaxMb) {
        this.serverId = serverId;
        this.recordedAt = Instant.now();
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.tps = tps;
        this.memoryUsedMb = memoryUsedMb;
        this.memoryMaxMb = memoryMaxMb;
    }

    public Long getId() { return id; }
    public String getServerId() { return serverId; }
    public Instant getRecordedAt() { return recordedAt; }
    public int getPlayerCount() { return playerCount; }
    public int getMaxPlayers() { return maxPlayers; }
    public double getTps() { return tps; }
    public int getMemoryUsedMb() { return memoryUsedMb; }
    public int getMemoryMaxMb() { return memoryMaxMb; }
}

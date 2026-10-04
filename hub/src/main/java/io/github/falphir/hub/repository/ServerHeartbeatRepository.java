package io.github.falphir.hub.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.github.falphir.hub.entity.ServerHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ServerHeartbeatRepository extends JpaRepository<ServerHeartbeat, Long> {

    List<ServerHeartbeat> findByServerIdAndRecordedAtAfterOrderByRecordedAtDesc(String serverId, Instant since);

    Optional<ServerHeartbeat> findFirstByServerIdOrderByRecordedAtDesc(String serverId);

    @Modifying
    @Query("delete from ServerHeartbeat h where h.recordedAt < :cutoff")
    int deleteByRecordedAtBefore(Instant cutoff);

    @Modifying
    @Query("delete from ServerHeartbeat h where h.serverId = :serverId")
    int deleteByServerId(String serverId);
}

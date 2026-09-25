package io.github.falphir.hub.repository;

import java.util.List;

import io.github.falphir.hub.entity.ServerHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerHeartbeatRepository extends JpaRepository<ServerHeartbeat, Long> {

    List<ServerHeartbeat> findByServerIdOrderByRecordedAtDesc(String serverId);
}

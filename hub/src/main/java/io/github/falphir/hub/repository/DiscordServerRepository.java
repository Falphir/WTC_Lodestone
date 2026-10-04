package io.github.falphir.hub.repository;

import java.util.List;

import io.github.falphir.hub.entity.DiscordServer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscordServerRepository extends JpaRepository<DiscordServer, String> {
    List<DiscordServer> findAllByOrderByCreatedAtAsc();
}

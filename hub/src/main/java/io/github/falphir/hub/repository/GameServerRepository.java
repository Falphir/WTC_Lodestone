package io.github.falphir.hub.repository;

import java.util.Optional;

import io.github.falphir.hub.entity.GameServer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameServerRepository extends JpaRepository<GameServer, String> {

    Optional<GameServer> findByTokenHash(String tokenHash);
}
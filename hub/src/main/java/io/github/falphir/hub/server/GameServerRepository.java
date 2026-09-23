package io.github.falphir.hub.server;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameServerRepository extends JpaRepository<GameServer, String> {

    Optional<GameServer> findByTokenHash(String tokenHash);
}
package io.github.falphir.hub.repository;

import java.util.Optional;

import io.github.falphir.hub.entity.GameServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GameServerRepository extends JpaRepository<GameServer, String> {

    Optional<GameServer> findByTokenHash(String tokenHash);

    /**
     * Bulk update, not a load-then-save: changing an entity's own @Id through the persistence
     * context is unreliable (a later flush would still look for the row under its old id).
     */
    @Modifying
    @Query("update GameServer g set g.name = :name, g.id = :newId where g.id = :oldId")
    int edit(String oldId, String name, String newId);
}
package io.github.falphir.hub.repository;

import java.util.List;

import io.github.falphir.hub.entity.WhitelistedPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhitelistedPlayerRepository extends JpaRepository<WhitelistedPlayer, String> {

    List<WhitelistedPlayer> findAllByOrderByNameAsc();
}

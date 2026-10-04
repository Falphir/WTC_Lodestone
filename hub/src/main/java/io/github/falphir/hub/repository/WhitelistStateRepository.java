package io.github.falphir.hub.repository;

import io.github.falphir.hub.entity.WhitelistState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhitelistStateRepository extends JpaRepository<WhitelistState, Integer> {
}

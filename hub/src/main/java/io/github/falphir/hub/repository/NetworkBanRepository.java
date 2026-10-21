package io.github.falphir.hub.repository;

import java.time.Instant;
import java.util.List;

import io.github.falphir.hub.entity.NetworkBan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NetworkBanRepository extends JpaRepository<NetworkBan, String> {

    List<NetworkBan> findAllByOrderByNameAsc();

    /** What servers apply: permanent bans and those still running. */
    @Query("select b from NetworkBan b where b.expiresAt is null or b.expiresAt > :now order by b.name")
    List<NetworkBan> findActive(Instant now);

    @Modifying
    @Query("delete from NetworkBan b where b.expiresAt <= :now")
    int deleteExpired(Instant now);
}

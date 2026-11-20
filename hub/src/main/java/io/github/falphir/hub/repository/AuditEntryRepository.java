package io.github.falphir.hub.repository;

import java.time.Instant;
import java.util.List;

import io.github.falphir.hub.entity.AuditEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /** Newest first, from just below {@code before}: the id is the order rows were written in. */
    List<AuditEntry> findByIdLessThanOrderByIdDesc(long before, Limit limit);

    @Modifying
    @Query("delete from AuditEntry e where e.at < :cutoff")
    int deleteByAtBefore(Instant cutoff);
}

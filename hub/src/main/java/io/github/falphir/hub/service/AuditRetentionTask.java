package io.github.falphir.hub.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.falphir.hub.repository.AuditEntryRepository;

/**
 * Deletes audit entries older than the retention period, once a day. They hold staff and player names
 * and Discord ids, so they're kept as long as they're useful for "who did that", not forever (PRIVACY.md).
 */
@Component
public class AuditRetentionTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditRetentionTask.class);

    private final AuditEntryRepository entries;
    private final Duration retention;

    public AuditRetentionTask(AuditEntryRepository entries, @Value("${lodestone.audit-retention-days:180}") int retentionDays) {
        this.entries = entries;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void pruneOldEntries() {
        int deleted = entries.deleteByAtBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            LOGGER.info("Pruned {} audit entr{} older than {} days", deleted, deleted == 1 ? "y" : "ies", retention.toDays());
        }
    }
}

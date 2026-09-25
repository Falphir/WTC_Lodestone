package io.github.falphir.hub.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.falphir.hub.repository.ServerHeartbeatRepository;

/** Deletes heartbeat rows older than the configured retention period, once a day. */
@Component
public class HeartbeatRetentionTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatRetentionTask.class);

    private final ServerHeartbeatRepository heartbeats;
    private final Duration retention;

    public HeartbeatRetentionTask(
            ServerHeartbeatRepository heartbeats,
            @Value("${lodestone.heartbeat-retention-days:7}") int retentionDays) {
        this.heartbeats = heartbeats;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void pruneOldHeartbeats() {
        int deleted = heartbeats.deleteByRecordedAtBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            LOGGER.info("Pruned {} heartbeat(s) older than {} days", deleted, retention.toDays());
        }
    }
}

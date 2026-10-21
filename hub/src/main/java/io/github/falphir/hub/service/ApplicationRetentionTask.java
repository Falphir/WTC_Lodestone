package io.github.falphir.hub.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.falphir.hub.repository.DiscordApplicationRepository;

/**
 * Deletes decided applications once nothing needs them, so the hub keeps applicants' data only as
 * long as it has a reason to. Pending ones stay until a decision; the public post in Discord is
 * not ours to delete here.
 */
@Component
public class ApplicationRetentionTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationRetentionTask.class);

    /** After approval the member role is what blocks re-applying; a day keeps /review answering "already reviewed". */
    static final Duration KEEP_APPROVED = Duration.ofDays(1);

    private final DiscordApplicationRepository applications;
    private final DiscordBotService discordBot;

    public ApplicationRetentionTask(DiscordApplicationRepository applications, DiscordBotService discordBot) {
        this.applications = applications;
        this.discordBot = discordBot;
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void pruneDecidedApplications() {
        Instant now = Instant.now();
        // A denial is needed exactly as long as it blocks re-applying
        Duration keepDenied = Duration.ofDays(discordBot.getConfig().getApplyCooldownDays());
        int deleted = applications.deleteDecidedBefore("approved", now.minus(KEEP_APPROVED).toEpochMilli())
                + applications.deleteDecidedBefore("denied", now.minus(keepDenied).toEpochMilli());
        if (deleted > 0) {
            LOGGER.info("Deleted {} decided application(s) past retention", deleted);
        }
    }
}

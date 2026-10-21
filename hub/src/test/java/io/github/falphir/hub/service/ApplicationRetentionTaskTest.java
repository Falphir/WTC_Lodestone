package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import io.github.falphir.hub.repository.DiscordApplicationRepository;

@SpringBootTest
@Transactional
class ApplicationRetentionTaskTest {

    @Autowired
    private ApplicationRetentionTask task;

    @Autowired
    private DiscordApplicationService service;

    @Autowired
    private DiscordBotService discordBot;

    @Autowired
    private DiscordApplicationRepository applications;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deletesDecidedApplicationsOnlyOnceTheyAreNoLongerNeeded() {
        discordBot.updateConfig(new DiscordBotService.Patch(
                null, null, null, null, null, null, null, 7, null, null, null));

        decided("test-ret-approved-old", "approved", Duration.ofDays(2));
        decided("test-ret-approved-new", "approved", Duration.ofHours(1));
        decided("test-ret-denied-old", "denied", Duration.ofDays(8));   // cooldown over
        decided("test-ret-denied-new", "denied", Duration.ofDays(3));   // still blocks re-applying
        service.setApplicationPending("test-ret-pending", "m1");
        applications.flush();

        task.pruneDecidedApplications();

        assertThat(applications.existsById("test-ret-approved-old")).isFalse();
        assertThat(applications.existsById("test-ret-denied-old")).isFalse();
        assertThat(applications.existsById("test-ret-approved-new")).isTrue();
        assertThat(applications.existsById("test-ret-denied-new")).isTrue();
        assertThat(applications.existsById("test-ret-pending")).isTrue();
    }

    /** decide() stamps "now", so the age is written straight to the column afterwards. */
    private void decided(String discordId, String status, Duration ago) {
        service.setApplicationPending(discordId, "m");
        service.setApplicationDecision(discordId, status);
        applications.flush();
        jdbc.update("UPDATE discord_applications SET decided_at = ? WHERE discord_id = ?",
                Instant.now().minus(ago).toEpochMilli(), discordId);
    }
}

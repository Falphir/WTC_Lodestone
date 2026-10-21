package io.github.falphir.hub.service;

import java.util.List;
import java.util.Optional;

import io.github.falphir.hub.entity.DiscordApplication;
import io.github.falphir.hub.repository.DiscordApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An applicant's latest /apply, tracked so the bot can enforce one open application at a time
 * and a re-apply cooldown -- used to be part of the bot's local SQLite (links.db), now hub-owned.
 * Unlike the account link itself (see WhitelistedPlayer.linkDiscord), an application can exist
 * for an account that was never (or not yet, or no longer) whitelisted, so this stays its own table.
 */
@Service
public class DiscordApplicationService {

    private final DiscordApplicationRepository applications;

    public DiscordApplicationService(DiscordApplicationRepository applications) {
        this.applications = applications;
    }

    @Transactional(readOnly = true)
    public Optional<DiscordApplication> getApplication(String discordId) {
        return applications.findById(discordId);
    }

    /**
     * Every application, or only those in one status. The bot lists the pending ones to offer
     * them as autocomplete choices on /review, so staff pick an applicant instead of naming one.
     */
    @Transactional(readOnly = true)
    public List<DiscordApplication> listApplications(String status) {
        return status == null || status.isBlank()
                ? applications.findAllByOrderByCreatedAtAsc()
                : applications.findByStatusOrderByCreatedAtAsc(status);
    }

    @Transactional
    public DiscordApplication setApplicationPending(String discordId, String messageId) {
        DiscordApplication app = applications.findById(discordId).orElseGet(() -> new DiscordApplication(discordId));
        app.markPending(messageId);
        return applications.save(app);
    }

    @Transactional
    public DiscordApplication setApplicationDecision(String discordId, String status) {
        DiscordApplication app = applications.findById(discordId).orElseGet(() -> new DiscordApplication(discordId));
        app.decide(status);
        return applications.save(app);
    }

    /** A no-op if there wasn't one, same as the SQLite DELETE it replaces. */
    @Transactional
    public void clearApplication(String discordId) {
        if (applications.existsById(discordId)) applications.deleteById(discordId);
    }
}

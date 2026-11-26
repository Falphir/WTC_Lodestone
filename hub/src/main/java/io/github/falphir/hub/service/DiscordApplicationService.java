package io.github.falphir.hub.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.falphir.hub.entity.DiscordApplication;
import io.github.falphir.hub.repository.DiscordApplicationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * An applicant's latest /apply, tracked so the bot can enforce one open application at a time
 * and a re-apply cooldown -- used to be part of the bot's local SQLite (links.db), now hub-owned.
 * Unlike the account link itself (see WhitelistedPlayer.linkDiscord), an application can exist
 * for an account that was never (or not yet, or no longer) whitelisted, so this stays its own table.
 */
@Service
public class DiscordApplicationService {

    private final DiscordApplicationRepository applications;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;

    public DiscordApplicationService(DiscordApplicationRepository applications, AuditLog audit,
            ApplicationEventPublisher events, ObjectMapper json) {
        this.applications = applications;
        this.audit = audit;
        this.events = events;
        this.json = json;
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

    /** Decided on the dashboard and not yet acted on by the bot, which polls for these. */
    @Transactional(readOnly = true)
    public List<DiscordApplication> listUnapplied() {
        return applications.findByBotPendingTrueOrderByDecidedAtAsc();
    }

    /** answers may be null: the dashboard then says they weren't recorded. */
    @Transactional
    public DiscordApplication setApplicationPending(String discordId, String messageId, String discordName,
            Map<String, String> answers) {
        DiscordApplication app = applications.findById(discordId).orElseGet(() -> new DiscordApplication(discordId));
        app.markPending(messageId, discordName, answers == null ? null : json.writeValueAsString(answers));
        events.publishEvent(HubEvent.applications());
        return applications.save(app);
    }

    /**
     * Refused unless the application is still pending, so the dashboard and /review can't both decide
     * it. {@code applied} says the caller already acted on it in Discord (the bot's /review); otherwise
     * the decision waits for the bot, which acknowledges it with {@link #markApplied}.
     */
    @Transactional
    public DiscordApplication setApplicationDecision(String discordId, String status, String reason, boolean applied) {
        DiscordApplication app = applications.findById(discordId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No application for this Discord account"));
        if (!"pending".equals(app.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This application was already " + app.getStatus());
        }
        boolean hasReason = "denied".equals(status) && reason != null && !reason.isBlank();
        app.decide(status, hasReason ? reason.strip() : null, !applied);
        // the applicant is only known by Discord id here; the bot names the staff member deciding
        audit.record("application." + status, "Discord " + discordId, null);
        events.publishEvent(HubEvent.applications());
        return applications.save(app);
    }

    /** The bot has acted on a dashboard decision. A no-op if the row is gone or wasn't waiting. */
    @Transactional
    public void markApplied(String discordId) {
        applications.findById(discordId).filter(DiscordApplication::isBotPending).ifPresent(app -> {
            app.markApplied();
            events.publishEvent(HubEvent.applications());
        });
    }

    /** A no-op if there wasn't one, same as the SQLite DELETE it replaces. */
    @Transactional
    public void clearApplication(String discordId) {
        if (applications.existsById(discordId)) {
            applications.deleteById(discordId);
            events.publishEvent(HubEvent.applications());
        }
    }
}

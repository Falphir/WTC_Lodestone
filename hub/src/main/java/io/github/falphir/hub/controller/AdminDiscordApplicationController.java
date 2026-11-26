package io.github.falphir.hub.controller;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.github.falphir.hub.entity.DiscordApplication;
import io.github.falphir.hub.service.DiscordApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/discord/applications")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminDiscordApplicationController {

    private final DiscordApplicationService service;

    public AdminDiscordApplicationController(DiscordApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List applications",
            description = "Oldest first. Pass `status` to narrow to pending/approved/denied -- the bot lists "
                    + "the pending ones as the choices on its /review command.")
    public List<ApplicationView> list(
            @RequestParam(required = false) @Pattern(regexp = "pending|approved|denied") String status) {
        return service.listApplications(status).stream().map(ApplicationView::from).toList();
    }

    @GetMapping("/unapplied")
    @Operation(summary = "List decisions the bot hasn't acted on",
            description = "Decided on the dashboard, so nobody has granted the member role or sent the deny DM yet. "
                    + "The bot polls this, acts on each one, then acknowledges it with PUT /{discordId}/applied.")
    public List<ApplicationView> unapplied() {
        return service.listUnapplied().stream().map(ApplicationView::from).toList();
    }

    @GetMapping("/{discordId}")
    @Operation(summary = "Get an applicant's latest application")
    public ResponseEntity<ApplicationView> get(@PathVariable String discordId) {
        return service.getApplication(discordId).map(ApplicationView::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{discordId}/pending")
    @Operation(summary = "Mark an application pending", description = "Overwrites any prior application for this Discord id.")
    public ApplicationView setPending(@PathVariable String discordId, @Valid @RequestBody PendingRequest request) {
        return ApplicationView.from(service.setApplicationPending(
                discordId, request.messageId(), request.discordName(), request.answers()));
    }

    /** answers: the /apply form by field (mc, age, referral, experience, why), for the dashboard. */
    public record PendingRequest(
            String messageId,
            @Size(max = 64) String discordName,
            @Size(max = 10) Map<@Size(max = 32) String, @Size(max = 1100) String> answers) {}

    @PutMapping("/{discordId}/decision")
    @Operation(summary = "Record a decision (approved/denied)",
            description = "409 unless the application is still pending, 404 if there is none. The reason is kept "
                    + "only on a denial. Unless `applied` is true (the bot already acted on it in Discord), the "
                    + "decision waits in GET /unapplied for the bot.")
    public ApplicationView setDecision(@PathVariable String discordId, @Valid @RequestBody DecisionRequest request) {
        return ApplicationView.from(service.setApplicationDecision(
                discordId, request.status(), request.reason(), Boolean.TRUE.equals(request.applied())));
    }

    /** applied is optional, so the dashboard can leave it out: Jackson 3 rejects a missing primitive. */
    public record DecisionRequest(
            @NotBlank @Pattern(regexp = "approved|denied") String status,
            @Size(max = 500) String reason,
            Boolean applied) {}

    @PutMapping("/{discordId}/applied")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Acknowledge a dashboard decision", description = "The bot has acted on it. A no-op if it wasn't waiting.")
    public void applied(@PathVariable String discordId) {
        service.markApplied(discordId);
    }

    @DeleteMapping("/{discordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear an applicant's application", description = "A no-op if there wasn't one.")
    public void clear(@PathVariable String discordId) {
        service.clearApplication(discordId);
    }

    /** answers is the stored JSON object passed through as-is, or null for older applications. */
    public record ApplicationView(String discordId, String status, String messageId, long createdAt, Long decidedAt,
            String discordName, @JsonRawValue String answers, String denyReason, boolean botPending) {
        static ApplicationView from(DiscordApplication a) {
            return new ApplicationView(a.getDiscordId(), a.getStatus(), a.getMessageId(), a.getCreatedAt(),
                    a.getDecidedAt(), a.getDiscordName(), a.getAnswers(), a.getDenyReason(), a.isBotPending());
        }
    }
}

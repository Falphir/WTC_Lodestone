package io.github.falphir.hub.controller;

import io.github.falphir.hub.entity.DiscordApplication;
import io.github.falphir.hub.service.DiscordApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/{discordId}")
    @Operation(summary = "Get an applicant's latest application")
    public ResponseEntity<ApplicationView> get(@PathVariable String discordId) {
        return service.getApplication(discordId).map(ApplicationView::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{discordId}/pending")
    @Operation(summary = "Mark an application pending", description = "Overwrites any prior application for this Discord id.")
    public ApplicationView setPending(@PathVariable String discordId, @Valid @RequestBody PendingRequest request) {
        return ApplicationView.from(service.setApplicationPending(discordId, request.messageId(), request.uuid(), request.username()));
    }

    public record PendingRequest(String messageId, String uuid, String username) {}

    @PutMapping("/{discordId}/decision")
    @Operation(summary = "Record a decision (approved/denied)")
    public ApplicationView setDecision(@PathVariable String discordId, @Valid @RequestBody DecisionRequest request) {
        return ApplicationView.from(service.setApplicationDecision(discordId, request.status()));
    }

    public record DecisionRequest(@NotBlank @Pattern(regexp = "approved|denied") String status) {}

    @DeleteMapping("/{discordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear an applicant's application", description = "A no-op if there wasn't one.")
    public void clear(@PathVariable String discordId) {
        service.clearApplication(discordId);
    }

    public record ApplicationView(String discordId, String status, String messageId, String uuid, String username,
            long createdAt, Long decidedAt) {
        static ApplicationView from(DiscordApplication a) {
            return new ApplicationView(a.getDiscordId(), a.getStatus(), a.getMessageId(), a.getMcUuid(),
                    a.getMcUsername(), a.getCreatedAt(), a.getDecidedAt());
        }
    }
}

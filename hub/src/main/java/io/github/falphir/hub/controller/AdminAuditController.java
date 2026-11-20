package io.github.falphir.hub.controller;

import java.time.Instant;
import java.util.List;

import io.github.falphir.hub.entity.AuditEntry;
import io.github.falphir.hub.service.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Admin", description = "Staff-only management endpoints")
@SecurityRequirement(name = "adminLogin")
@SecurityRequirement(name = "adminToken")
public class AdminAuditController {

    private final AuditLog audit;

    public AdminAuditController(AuditLog audit) {
        this.audit = audit;
    }

    @GetMapping
    @Operation(summary = "Audit log", description = "Who changed what and when, newest first. For the next page, pass the last "
            + "entry's id as `before`. `via` is the Discord staff member the bot acted for, null for anything else.")
    public List<EntryView> list(@RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return audit.page(before, limit).stream().map(EntryView::from).toList();
    }

    public record EntryView(long id, Instant at, String actor, String via, String action, String target, String detail) {
        static EntryView from(AuditEntry e) {
            return new EntryView(e.getId(), e.getAt(), e.getActor(), e.getVia(), e.getAction(), e.getTarget(), e.getDetail());
        }
    }
}

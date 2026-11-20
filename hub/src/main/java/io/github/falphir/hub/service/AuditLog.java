package io.github.falphir.hub.service;

import java.time.Instant;
import java.util.List;

import io.github.falphir.hub.entity.AuditEntry;
import io.github.falphir.hub.repository.AuditEntryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The admin audit log. Every service method that changes something on an admin's behalf calls
 * {@link #record}. Who did it is read from the request rather than passed in: the authenticated
 * account, and for the bot's calls the Discord staff member it names in {@link #ON_BEHALF_OF}.
 */
@Service
public class AuditLog {

    /** Sent by the bot with every call it makes for someone in Discord: "name (id)". */
    public static final String ON_BEHALF_OF = "X-Lodestone-On-Behalf-Of";

    private final AuditEntryRepository entries;
    private final ApplicationEventPublisher events;

    public AuditLog(AuditEntryRepository entries, ApplicationEventPublisher events) {
        this.entries = entries;
        this.events = events;
    }

    /**
     * Records a change. Joins the caller's transaction, so a change that rolls back leaves no row.
     * {@code target} is what it was done to, readable (a player's or server's name); {@code detail}
     * says more, or is null.
     */
    @Transactional
    public void record(String action, String target, String detail) {
        entries.save(new AuditEntry(Instant.now(), actor(), via(), cut(action, 40), cut(target == null ? "" : target, 120),
                cut(detail, 500)));
        events.publishEvent(HubEvent.audit());
    }

    /** Newest first, below the id {@code before} (null: from the newest). */
    @Transactional(readOnly = true)
    public List<AuditEntry> page(Long before, int limit) {
        return entries.findByIdLessThanOrderByIdDesc(before == null ? Long.MAX_VALUE : before, Limit.of(limit));
    }

    private static String actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                ? "system" : cut(auth.getName(), 64);
    }

    /** Taken as given: only an admin can send it, and the row still names that admin as the actor. */
    private static String via() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request)) return null;
        String header = request.getRequest().getHeader(ON_BEHALF_OF);
        if (header == null) return null;
        String clean = header.replaceAll("\\p{Cntrl}", "").trim();
        return clean.isEmpty() ? null : cut(clean, 120);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

package io.github.falphir.hub.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One admin change: who, through what, to what, when. Rows are never updated, only inserted. */
@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant at;

    @Column(nullable = false, length = 64)
    private String actor;

    @Column(length = 120)
    private String via;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(nullable = false, length = 120)
    private String target;

    @Column(length = 500)
    private String detail;

    protected AuditEntry() {}

    public AuditEntry(Instant at, String actor, String via, String action, String target, String detail) {
        this.at = at;
        this.actor = actor;
        this.via = via;
        this.action = action;
        this.target = target;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Instant getAt() { return at; }
    public String getActor() { return actor; }
    public String getVia() { return via; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getDetail() { return detail; }
}

package io.github.falphir.hub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Singleton row (id always 1): what a network-banned player is told when refused or kicked. */
@Entity
@Table(name = "ban_settings")
public class BanSettings {

    @Id
    private int id;

    /** Null means the built-in default. */
    @Column(length = 500)
    private String message;

    protected BanSettings() {
        // required by JPA
    }

    public BanSettings(int id) {
        this.id = id;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

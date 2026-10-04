package io.github.falphir.hub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Singleton row (id always 1): whether the network whitelist has ever had a player on it. */
@Entity
@Table(name = "whitelist_state")
public class WhitelistState {

    @Id
    private int id;

    @Column(name = "ever_populated", nullable = false)
    private boolean everPopulated;

    protected WhitelistState() {
        // required by JPA
    }

    public WhitelistState(boolean everPopulated) {
        this.id = 1;
        this.everPopulated = everPopulated;
    }

    public boolean isEverPopulated() { return everPopulated; }
    public void setEverPopulated(boolean everPopulated) { this.everPopulated = everPopulated; }
}

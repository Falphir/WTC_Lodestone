package io.github.falphir.hub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One of the network's MOTDs. {@code text} holds real newlines and {modpack}/{version}/{name} placeholders. */
@Entity
@Table(name = "motd_templates")
public class MotdTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 300)
    private String text;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    protected MotdTemplate() {
        // required by JPA
    }

    public MotdTemplate(String name, String text) {
        this.name = name;
        this.text = text;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}

package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.falphir.hub.entity.AuditEntry;
import io.github.falphir.hub.repository.AuditEntryRepository;

/** Each kind of admin change leaves one audit row naming the admin; a refused one leaves none. */
@SpringBootTest
@Transactional
class AuditRecordingTest {

    private static final String ALICE = "0f3a1b2c-0000-4000-8000-0000000000a1";

    @MockitoBean
    private MojangProfiles mojang;

    @Autowired private AuditEntryRepository entries;
    @Autowired private WhitelistService whitelist;
    @Autowired private BanService bans;
    @Autowired private ServerService servers;
    @Autowired private MotdService motds;
    @Autowired private AdminUserService admins;
    @Autowired private DiscordBotService discord;
    @Autowired private DiscordApplicationService applications;

    @BeforeEach
    void signedInAsAnAdmin() {
        entries.deleteAll(); // rolled back with the test
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("audit-admin", null, "ROLE_ADMIN"));
        when(mojang.lookup("Alice")).thenReturn(Optional.of(new MojangProfiles.Profile(ALICE, "Alice")));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void whitelistChanges() {
        whitelist.add("Alice", "audit-admin");
        whitelist.linkDiscord(ALICE, "123", "Alice");
        whitelist.setKickMessage("Not on the list");
        whitelist.remove(ALICE);

        assertThat(rows()).containsExactly(
                "whitelist.add Alice " + ALICE,
                "whitelist.link Alice Discord 123",
                "whitelist.kick-message Kick message null",
                "whitelist.remove Alice " + ALICE + ", and its link to Discord 123");
        assertThat(entries.findAll()).extracting(AuditEntry::getActor).containsOnly("audit-admin");
    }

    @Test
    void aRefusedChangeLeavesNoRow() {
        whitelist.add("Alice", "audit-admin");
        entries.deleteAll();

        assertThatThrownBy(() -> whitelist.add("Alice", "audit-admin")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> bans.unban(ALICE)).isInstanceOf(ResponseStatusException.class);

        assertThat(entries.findAll()).isEmpty();
    }

    @Test
    void banChanges() {
        bans.ban("Alice", "griefing", null, "audit-admin");
        bans.unban(ALICE);

        assertThat(rows()).containsExactly("ban.add Alice griefing, permanent", "ban.remove Alice " + ALICE);
    }

    @Test
    void serverChanges() {
        servers.register("audit-srv", "Audit", null);
        servers.edit("audit-srv", new ServerService.Patch(null, "Audit 2", null, "ATM10", null, null, null, null, null, null));
        servers.resetToken("audit-srv");
        servers.remove("audit-srv");

        assertThat(rows()).containsExactly(
                "server.register audit-srv Audit",
                "server.edit audit-srv name, modpack", // which fields, not their values
                "server.reset-token audit-srv null",
                "server.remove audit-srv null");
    }

    @Test
    void motdAndAdminChanges() {
        long id = motds.create("audit-motd", "Hello").id();
        motds.update(id, "audit-motd-2", "Hello again");
        motds.delete(id);
        long admin = admins.create("audit-new-admin", "a-password").getId();
        admins.update(admin, "another-password", false);
        admins.delete(admin);

        assertThat(rows()).contains(
                "motd.create audit-motd null",
                "motd.edit audit-motd-2 renamed from audit-motd",
                "motd.delete audit-motd-2 null",
                "admin.create audit-new-admin null",
                "admin.edit audit-new-admin password changed, disabled",
                "admin.delete audit-new-admin null");
    }

    @Test
    void discordChanges() {
        discord.updateConfig(new DiscordBotService.Patch(null, null, null, null, null, null, 16, null, "WTC", null, null));
        applications.setApplicationDecision("456", "approved");

        assertThat(rows()).containsExactly(
                "discord.config Discord bot settings minAge, brandName",
                "application.approved Discord 456 null");
    }

    /** "action target detail", oldest first. */
    private List<String> rows() {
        return entries.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(e -> e.getAction() + " " + e.getTarget() + " " + e.getDetail())
                .toList();
    }
}

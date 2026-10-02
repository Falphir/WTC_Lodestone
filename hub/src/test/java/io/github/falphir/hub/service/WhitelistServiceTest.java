package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.falphir.hub.entity.WhitelistedPlayer;

@SpringBootTest
@Transactional
class WhitelistServiceTest {

    private static final String ALICE = "0f3a1b2c-0000-4000-8000-000000000001";
    private static final String BOB = "0f3a1b2c-0000-4000-8000-000000000002";

    @Autowired
    private WhitelistService service;

    @MockitoBean
    private MojangProfiles mojang;

    @Test
    void importSkipsDuplicatesAndExistingPlayers() {
        service.importEntries(List.of(new WhitelistService.Entry(ALICE, "Alice")), "admin");

        int added = service.importEntries(List.of(
                new WhitelistService.Entry(ALICE.toUpperCase(), "Alice"),
                new WhitelistService.Entry(BOB, "Bob"),
                new WhitelistService.Entry(BOB, "Bob")), "admin");

        assertThat(added).isEqualTo(1);
        assertThat(service.list()).extracting(WhitelistedPlayer::getUuid).containsExactly(ALICE, BOB);
    }

    @Test
    void addUsesMojangNameAndRefusesDuplicates() {
        when(mojang.lookup("alice")).thenReturn(Optional.of(new MojangProfiles.Profile(ALICE, "Alice")));

        assertThat(service.add("alice", "admin").getName()).isEqualTo("Alice");
        assertThatThrownBy(() -> service.add("alice", "admin")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void dashesMojangUuids() {
        assertThat(MojangProfiles.dashed("0F3A1B2C000040008000000000000001")).isEqualTo(ALICE);
    }
}

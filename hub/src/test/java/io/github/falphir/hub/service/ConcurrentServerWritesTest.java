package io.github.falphir.hub.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.falphir.hub.entity.GameServer;
import io.github.falphir.hub.repository.GameServerRepository;

/**
 * A mod's startup sends hello, whitelist-applied and bans-applied at once, and each writes the same
 * servers row. On MariaDB 11.8 (innodb_snapshot_isolation on) overlapping writes used to fail with
 * "Record has changed since last read"; on older versions the last one silently put back the
 * others' columns. Not @Transactional: the point is two transactions that really commit.
 */
@SpringBootTest
class ConcurrentServerWritesTest {

    private static final String SERVER = "concurrent-writes-test";

    @Autowired
    private ServerService servers;

    @Autowired
    private GameServerRepository repository;

    @Autowired
    private PlatformTransactionManager transactions;

    @AfterEach
    void cleanUp() {
        if (repository.existsById(SERVER)) servers.remove(SERVER);
    }

    @Test
    void overlappingWritesToOneServerBothLand() {
        servers.register(SERVER, "Concurrent Writes", null);
        TransactionTemplate outer = new TransactionTemplate(transactions);
        TransactionTemplate inner = new TransactionTemplate(transactions);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // the outer one reads the row, the inner one changes and commits it in the meantime,
        // then the outer one writes its own column -- what hello vs bans-applied do at startup
        outer.executeWithoutResult(status -> {
            GameServer seen = repository.findById(SERVER).orElseThrow();
            inner.executeWithoutResult(s -> servers.bansApplied(SERVER, "ban-v1"));
            seen.whitelistApplied("whitelist-v1");
        });

        GameServer after = repository.findById(SERVER).orElseThrow();
        assertThat(after.getWhitelistVersion()).isEqualTo("whitelist-v1");
        assertThat(after.getBanVersion()).isEqualTo("ban-v1"); // not overwritten by the outer write
    }
}

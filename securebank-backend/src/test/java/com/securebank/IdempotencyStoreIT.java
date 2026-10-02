package com.securebank;

import static org.assertj.core.api.Assertions.assertThat;

import com.securebank.shared.application.IdempotencyStore;
import com.securebank.shared.application.IdempotencyStore.Claim;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Ciclo de vida de uma chave no PostgreSQL, com o relógio sob controle (retomada de chave abandonada ou expirada). */
@SpringBootTest
@Import(TestcontainersConfig.class)
class IdempotencyStoreIT {

    private static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired IdempotencyStore store;

    private static String key() {
        return "key-" + UUID.randomUUID();
    }

    @Test
    void firstClaimProceedsAndACompletedOneIsReplayed() {
        String key = key();

        assertThat(store.claim("u1", key, "fp", T0)).isInstanceOf(Claim.Proceed.class);
        assertThat(store.claim("u1", key, "fp", T0.plusSeconds(1))).isInstanceOf(Claim.InProgress.class);

        store.complete("u1", key, 201, "{\"ok\":true}");

        assertThat(store.claim("u1", key, "fp", T0.plusSeconds(2)))
                .isEqualTo(new Claim.Replay(201, "{\"ok\":true}"));
    }

    @Test
    void aDifferentRequestWithTheSameKeyIsAMismatchAndScopesAreIndependent() {
        String key = key();
        store.claim("u1", key, "fp-A", T0);
        store.complete("u1", key, 201, "{}");

        assertThat(store.claim("u1", key, "fp-B", T0.plusSeconds(1))).isInstanceOf(Claim.Mismatch.class);
        assertThat(store.claim("u2", key, "fp-B", T0.plusSeconds(1))).isInstanceOf(Claim.Proceed.class);
    }

    @Test
    void releasedKeysCanBeClaimedAgain() {
        String key = key();
        store.claim("u1", key, "fp", T0);

        store.release("u1", key);

        assertThat(store.claim("u1", key, "fp", T0.plusSeconds(1))).isInstanceOf(Claim.Proceed.class);
    }

    @Test
    void anAbandonedReservationIsTakenOverAfterTheTimeout() {
        String key = key();
        store.claim("u1", key, "fp", T0); // a JVM "caiu" aqui: nunca concluiu

        assertThat(store.claim("u1", key, "fp", T0.plus(Duration.ofSeconds(30)))).isInstanceOf(Claim.InProgress.class);
        assertThat(store.claim("u1", key, "fp", T0.plus(Duration.ofSeconds(61)))).isInstanceOf(Claim.Proceed.class);
        // a retomada é uma nova reserva: quem chegar logo depois vê "em andamento" de novo
        assertThat(store.claim("u1", key, "fp", T0.plus(Duration.ofSeconds(62)))).isInstanceOf(Claim.InProgress.class);
    }

    @Test
    void anExpiredKeyIsReusableEvenWithADifferentRequest() {
        String key = key();
        store.claim("u1", key, "fp-A", T0);
        store.complete("u1", key, 201, "{}");

        Instant afterRetention = T0.plus(Duration.ofHours(25));

        assertThat(store.claim("u1", key, "fp-B", afterRetention)).isInstanceOf(Claim.Proceed.class);
    }
}

package com.securebank.pix.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PixTransferId;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PixChargeTest {

    private static final Instant NOW = Instant.parse("2026-10-02T14:30:00Z");

    private final CustomerId owner = CustomerId.newId();
    private final AccountId account = AccountId.newId();

    private PixCharge charge() {
        return PixCharge.create(owner, account, Money.brl("75.00"), "Pedido 42", Duration.ofHours(1), NOW);
    }

    // ---------- criação
    @Test
    void newChargeIsActiveWithAFixedAmountAndAnExpiry() {
        PixCharge c = charge();

        assertThat(c.status()).isEqualTo(PixChargeStatus.ACTIVE);
        assertThat(c.amount()).isEqualTo(Money.brl("75.00"));
        assertThat(c.description()).isEqualTo("Pedido 42");
        assertThat(c.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(c.createdAt()).isEqualTo(NOW);
        assertThat(c.paidAt()).isNull();
        assertThat(c.paidPixId()).isNull();
        assertThat(c.accountId()).isEqualTo(account);
        assertThat(c.customerId()).isEqualTo(owner);
        assertThat(c.isOwnedBy(owner)).isTrue();
        assertThat(c.isOwnedBy(CustomerId.newId())).isFalse();
    }

    @Test
    void theDefaultValidityIsOneDay() {
        PixCharge c = PixCharge.create(owner, account, Money.brl("1.00"), null, null, NOW);
        assertThat(c.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void validityMustBeBetweenOneMinuteAndSevenDaysInclusive() {
        assertThat(PixCharge.create(owner, account, Money.brl("1.00"), null, Duration.ofMinutes(1), NOW).expiresAt())
                .isEqualTo(NOW.plusSeconds(60));
        assertThat(PixCharge.create(owner, account, Money.brl("1.00"), null, Duration.ofDays(7), NOW).expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThatThrownBy(() -> PixCharge.create(owner, account, Money.brl("1.00"), null, Duration.ofSeconds(59), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixCharge.create(owner, account, Money.brl("1.00"), null, Duration.ofDays(7).plusSeconds(1), NOW))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void amountMustBePositiveAndDescriptionIsTrimmedAndLimited() {
        assertThatThrownBy(() -> PixCharge.create(owner, account, Money.zero(Money.BRL), null, null, NOW)).isInstanceOf(InvalidValueException.class);
        assertThat(PixCharge.create(owner, account, Money.brl("1.00"), "  oi  ", null, NOW).description()).isEqualTo("oi");
        assertThat(PixCharge.create(owner, account, Money.brl("1.00"), "   ", null, NOW).description()).isNull();
        assertThat(PixCharge.create(owner, account, Money.brl("1.00"), "x".repeat(140), null, NOW).description()).hasSize(140);
        assertThatThrownBy(() -> PixCharge.create(owner, account, Money.brl("1.00"), "x".repeat(141), null, NOW)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void creationRequiresItsInputs() {
        assertThatThrownBy(() -> PixCharge.create(null, account, Money.brl("1.00"), null, null, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixCharge.create(owner, null, Money.brl("1.00"), null, null, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixCharge.create(owner, account, null, null, null, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixCharge.create(owner, account, Money.brl("1.00"), null, null, null)).isInstanceOf(InvalidValueException.class);
    }

    // ---------- txid
    @Test
    void theTxidIs32AlphanumericCharactersAndDiffersEveryTime() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String txid = charge().txid();
            assertThat(txid).matches("[A-Za-z0-9]{32}");
            assertThat(PixCharge.isValidTxid(txid)).isTrue();
            seen.add(txid);
        }
        assertThat(seen).hasSize(200);
    }

    @Test
    void theSameRandomSourceGivesTheSameTxid() {
        String a = PixCharge.create(owner, account, Money.brl("1.00"), null, null, NOW, new Random(5)).txid();
        String b = PixCharge.create(owner, account, Money.brl("1.00"), null, null, NOW, new Random(5)).txid();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void txidValidationAcceptsOnlyTheBrCodeFormat() {
        assertThat(PixCharge.isValidTxid("a".repeat(26))).isTrue();
        assertThat(PixCharge.isValidTxid("a".repeat(35))).isTrue();
        assertThat(PixCharge.isValidTxid("a".repeat(25))).isFalse();
        assertThat(PixCharge.isValidTxid("a".repeat(36))).isFalse();
        assertThat(PixCharge.isValidTxid("a".repeat(25) + "-")).isFalse();
        assertThat(PixCharge.isValidTxid("a".repeat(25) + " ")).isFalse();
        assertThat(PixCharge.isValidTxid("../../etc/passwd/................")).isFalse();
        assertThat(PixCharge.isValidTxid(null)).isFalse();
    }

    // ---------- estado
    @Test
    void anActiveChargeBecomesExpiredWhenTheDeadlinePasses() {
        PixCharge c = charge();

        assertThat(c.statusAt(NOW)).isEqualTo(PixChargeStatus.ACTIVE);
        assertThat(c.statusAt(c.expiresAt().minusMillis(1))).isEqualTo(PixChargeStatus.ACTIVE);
        assertThat(c.statusAt(c.expiresAt())).isEqualTo(PixChargeStatus.EXPIRED); // no instante exato já expirou
        assertThat(c.statusAt(c.expiresAt().plusSeconds(1))).isEqualTo(PixChargeStatus.EXPIRED);
        assertThat(c.status()).isEqualTo(PixChargeStatus.ACTIVE); // EXPIRED é derivado, nunca gravado
    }

    @Test
    void payingMarksItPaidOnce() {
        PixCharge c = charge();
        PixTransferId pix = PixTransferId.newId();

        c.markPaid(pix, NOW.plusSeconds(60));

        assertThat(c.status()).isEqualTo(PixChargeStatus.PAID);
        assertThat(c.paidAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(c.paidPixId()).isEqualTo(pix);
        assertThat(c.statusAt(NOW.plus(Duration.ofDays(30)))).isEqualTo(PixChargeStatus.PAID); // paga não "expira"
        assertThatThrownBy(() -> c.markPaid(PixTransferId.newId(), NOW.plusSeconds(61)))
                .isInstanceOf(PixChargeException.class).extracting("code").isEqualTo("PIX_CHARGE_NOT_PAYABLE");
    }

    @Test
    void anExpiredChargeCannotBePaid() {
        PixCharge c = charge();

        assertThatThrownBy(() -> c.ensurePayable(c.expiresAt())).isInstanceOf(PixChargeException.class)
                .extracting("code").isEqualTo("PIX_CHARGE_EXPIRED");
        assertThatThrownBy(() -> c.markPaid(PixTransferId.newId(), c.expiresAt().plusSeconds(1))).isInstanceOf(PixChargeException.class);
        assertThat(c.status()).isEqualTo(PixChargeStatus.ACTIVE);
    }

    @Test
    void aCanceledChargeCannotBePaidNorCanceledAgain() {
        PixCharge c = charge();

        c.cancel();

        assertThat(c.status()).isEqualTo(PixChargeStatus.CANCELED);
        assertThatThrownBy(() -> c.ensurePayable(NOW)).isInstanceOf(PixChargeException.class)
                .extracting("code").isEqualTo("PIX_CHARGE_NOT_PAYABLE");
        assertThatThrownBy(c::cancel).isInstanceOf(PixChargeException.class).extracting("code").isEqualTo("PIX_CHARGE_NOT_CANCELABLE");
    }

    @Test
    void aPaidChargeCannotBeCanceled() {
        PixCharge c = charge();
        c.markPaid(PixTransferId.newId(), NOW);

        assertThatThrownBy(c::cancel).isInstanceOf(PixChargeException.class);
        assertThat(c.status()).isEqualTo(PixChargeStatus.PAID);
    }

    @Test
    void restoreKeepsEveryField() {
        PixTransferId pix = PixTransferId.newId();
        PixCharge c = PixCharge.restore("a".repeat(32), owner, account, Money.brl("9.90"), "x", PixChargeStatus.PAID,
                NOW.plusSeconds(100), NOW, NOW.plusSeconds(5), pix);

        assertThat(c.txid()).isEqualTo("a".repeat(32));
        assertThat(c.paidPixId()).isEqualTo(pix);
        assertThat(c.paidAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(c.status()).isEqualTo(PixChargeStatus.PAID);
    }

    @Test
    void aPixCanBeLinkedToOneChargeOnly() {
        PixTransfer pix = PixTransfer.create(account, AccountId.newId(), Money.brl("1.00"), null, "k", "A", "B",
                "E00000000202610021430abcdefghijk", NOW);

        pix.linkToCharge("t".repeat(32));

        assertThat(pix.chargeTxid()).isEqualTo("t".repeat(32));
        assertThatThrownBy(() -> pix.linkToCharge("u".repeat(32))).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.create(account, AccountId.newId(), Money.brl("1.00"), null, "k", "A", "B",
                "E00000000202610021430abcdefghijk", NOW).linkToCharge(null)).isInstanceOf(InvalidValueException.class);
    }
}

package com.securebank.pix.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PixTransferId;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PixScheduleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final Instant NOW = Instant.parse("2026-10-06T15:00:00Z");

    private final CustomerId owner = CustomerId.newId();
    private final AccountId account = AccountId.newId();

    private PixSchedule schedule(LocalDate date) {
        return PixSchedule.create(owner, account, "bob@example.com", "Bob L***", Money.brl("80.00"), "aluguel", date, TODAY, NOW);
    }

    // ---------- criação
    @Test
    void aNewScheduleWaitsForItsDate() {
        PixSchedule s = schedule(TODAY.plusDays(3));

        assertThat(s.status()).isEqualTo(PixScheduleStatus.SCHEDULED);
        assertThat(s.scheduledFor()).isEqualTo(TODAY.plusDays(3));
        assertThat(s.key()).isEqualTo("bob@example.com");
        assertThat(s.destinationName()).isEqualTo("Bob L***");
        assertThat(s.amount()).isEqualTo(Money.brl("80.00"));
        assertThat(s.message()).isEqualTo("aluguel");
        assertThat(s.failureReason()).isNull();
        assertThat(s.executedPixId()).isNull();
        assertThat(s.createdAt()).isEqualTo(NOW);
        assertThat(s.sourceAccountId()).isEqualTo(account);
        assertThat(s.isOwnedBy(owner)).isTrue();
        assertThat(s.isOwnedBy(CustomerId.newId())).isFalse();
    }

    @Test
    void theDateMustBeFromTomorrowUpTo365DaysAhead() {
        assertThat(schedule(TODAY.plusDays(1)).scheduledFor()).isEqualTo(TODAY.plusDays(1));
        assertThat(schedule(TODAY.plusDays(365)).scheduledFor()).isEqualTo(TODAY.plusDays(365));
        assertThatThrownBy(() -> schedule(TODAY)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> schedule(TODAY.minusDays(1))).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> schedule(TODAY.plusDays(366))).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void amountAndMessageAreValidated() {
        LocalDate date = TODAY.plusDays(1);
        assertThatThrownBy(() -> PixSchedule.create(owner, account, "k", "N", Money.zero(Money.BRL), null, date, TODAY, NOW)).isInstanceOf(InvalidValueException.class);
        assertThat(PixSchedule.create(owner, account, "k", "N", Money.brl("1.00"), "  oi ", date, TODAY, NOW).message()).isEqualTo("oi");
        assertThat(PixSchedule.create(owner, account, "k", "N", Money.brl("1.00"), "  ", date, TODAY, NOW).message()).isNull();
        assertThat(PixSchedule.create(owner, account, "k", "N", Money.brl("1.00"), "x".repeat(140), date, TODAY, NOW).message()).hasSize(140);
        assertThatThrownBy(() -> PixSchedule.create(owner, account, "k", "N", Money.brl("1.00"), "x".repeat(141), date, TODAY, NOW)).isInstanceOf(InvalidValueException.class);
        assertThat(PixSchedule.create(owner, account, "k", null, Money.brl("1.00"), null, date, TODAY, NOW).destinationName()).isEmpty();
    }

    @Test
    void creationRequiresItsInputs() {
        LocalDate date = TODAY.plusDays(1);
        Money m = Money.brl("1.00");
        assertThatThrownBy(() -> PixSchedule.create(null, account, "k", "N", m, null, date, TODAY, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixSchedule.create(owner, null, "k", "N", m, null, date, TODAY, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixSchedule.create(owner, account, null, "N", m, null, date, TODAY, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixSchedule.create(owner, account, " ", "N", m, null, date, TODAY, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixSchedule.create(owner, account, "k", "N", null, null, date, TODAY, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixSchedule.create(owner, account, "k", "N", m, null, null, TODAY, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixSchedule.create(owner, account, "k", "N", m, null, date, null, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixSchedule.create(owner, account, "k", "N", m, null, date, TODAY, null)).isInstanceOf(InvalidValueException.class);
    }

    // ---------- vencimento
    @Test
    void itIsDueOnItsDateAndAfterButNotBefore() {
        PixSchedule s = schedule(TODAY.plusDays(3));

        assertThat(s.isDueOn(TODAY.plusDays(2))).isFalse();
        assertThat(s.isDueOn(TODAY.plusDays(3))).isTrue();  // no próprio dia
        assertThat(s.isDueOn(TODAY.plusDays(10))).isTrue(); // atrasado (agendador parado): ainda executa
    }

    @Test
    void onlyAScheduledPixIsEverDue() {
        PixSchedule executed = schedule(TODAY.plusDays(1));
        executed.markExecuted(PixTransferId.newId(), NOW);
        PixSchedule canceled = schedule(TODAY.plusDays(1));
        canceled.cancel(NOW);

        assertThat(executed.isDueOn(TODAY.plusDays(5))).isFalse();
        assertThat(canceled.isDueOn(TODAY.plusDays(5))).isFalse();
    }

    // ---------- transições
    @Test
    void executingRecordsThePixAndFreezesTheSchedule() {
        PixSchedule s = schedule(TODAY.plusDays(1));
        PixTransferId pix = PixTransferId.newId();
        Instant later = NOW.plusSeconds(86400);

        s.markExecuted(pix, later);

        assertThat(s.status()).isEqualTo(PixScheduleStatus.EXECUTED);
        assertThat(s.executedPixId()).isEqualTo(pix);
        assertThat(s.updatedAt()).isEqualTo(later);
        assertThatThrownBy(() -> s.markExecuted(PixTransferId.newId(), later)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> s.markFailed("X", later)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> s.cancel(later)).isInstanceOf(PixScheduleException.class);
    }

    @Test
    void failingKeepsTheReasonTruncatedAndIsFinal() {
        PixSchedule s = schedule(TODAY.plusDays(1));

        s.markFailed("INSUFFICIENT_FUNDS", NOW.plusSeconds(10));

        assertThat(s.status()).isEqualTo(PixScheduleStatus.FAILED);
        assertThat(s.failureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(s.executedPixId()).isNull();
        assertThatThrownBy(() -> s.markFailed("OUTRO", NOW)).isInstanceOf(InvalidStateTransitionException.class);

        PixSchedule long1 = schedule(TODAY.plusDays(1));
        long1.markFailed("X".repeat(100), NOW);
        assertThat(long1.failureReason()).hasSize(60);
        PixSchedule none = schedule(TODAY.plusDays(1));
        none.markFailed(null, NOW);
        assertThat(none.failureReason()).isEqualTo("UNKNOWN");
    }

    @Test
    void cancelingOnlyWorksWhileScheduled() {
        PixSchedule s = schedule(TODAY.plusDays(1));

        s.cancel(NOW.plusSeconds(5));

        assertThat(s.status()).isEqualTo(PixScheduleStatus.CANCELED);
        assertThat(s.updatedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThatThrownBy(() -> s.cancel(NOW)).isInstanceOf(PixScheduleException.class).extracting("code").isEqualTo("PIX_SCHEDULE_NOT_CANCELABLE");
        PixSchedule failed = schedule(TODAY.plusDays(1));
        failed.markFailed("X", NOW);
        assertThatThrownBy(() -> failed.cancel(NOW)).isInstanceOf(PixScheduleException.class);
    }

    @Test
    void restoreKeepsEveryField() {
        PixTransferId pix = PixTransferId.newId();
        PixSchedule s = PixSchedule.restore(com.securebank.shared.domain.PixScheduleId.newId(), owner, account, "k", "N",
                Money.brl("5.00"), "m", TODAY.plusDays(2), PixScheduleStatus.EXECUTED, null, pix, NOW, NOW.plusSeconds(1));

        assertThat(s.status()).isEqualTo(PixScheduleStatus.EXECUTED);
        assertThat(s.executedPixId()).isEqualTo(pix);
        assertThat(s.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }
}

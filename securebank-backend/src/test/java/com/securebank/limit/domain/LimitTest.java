package com.securebank.limit.domain;

import static com.securebank.DomainFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import org.junit.jupiter.api.Test;

class LimitTest {

    private final Limit transferLimit = Limit.defaultFor(AccountId.newId(), LimitType.TRANSFER, NOW); // 5.000 / 10.000

    @Test
    void amountExactlyAtTheLimitIsAllowedAndOneCentOverIsNot() {
        assertThatCode(() -> transferLimit.check(Money.brl("5000.00"), Money.zero(Money.BRL))).doesNotThrowAnyException();

        assertThatThrownBy(() -> transferLimit.check(Money.brl("5000.01"), Money.zero(Money.BRL)))
                .isInstanceOfSatisfying(LimitExceededException.class,
                        e -> assertThat(e.scope()).isEqualTo(LimitExceededException.Scope.PER_OPERATION));
    }

    @Test
    void dailyLimitCountsWhatWasAlreadyUsedToday() {
        Money used = Money.brl("9000.00");

        assertThatCode(() -> transferLimit.check(Money.brl("1000.00"), used)).doesNotThrowAnyException();
        assertThatThrownBy(() -> transferLimit.check(Money.brl("1000.01"), used))
                .isInstanceOfSatisfying(LimitExceededException.class,
                        e -> assertThat(e.scope()).isEqualTo(LimitExceededException.Scope.DAILY));
    }

    @Test
    void changeValidatesTheRelationBetweenTheTwoCaps() {
        transferLimit.change(Money.brl("200.00"), Money.brl("1000.00"), NOW);
        assertThat(transferLimit.perOperation()).isEqualTo(Money.brl("200.00"));

        assertThatThrownBy(() -> transferLimit.change(Money.brl("500.00"), Money.brl("100.00"), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> transferLimit.change(Money.zero(Money.BRL), Money.brl("100.00"), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThat(transferLimit.daily()).isEqualTo(Money.brl("1000.00")); // rejeitado não altera
    }

    @Test
    void everyOperationTypeHasADefault() {
        for (LimitType type : LimitType.values()) {
            Limit limit = Limit.defaultFor(AccountId.newId(), type, NOW);
            assertThat(limit.perOperation().isPositive()).isTrue();
            assertThat(limit.daily().isLessThan(limit.perOperation())).isFalse();
        }
    }
}

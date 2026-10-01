package com.securebank.customer.domain;

import static com.securebank.DomainFixtures.NOW;
import static com.securebank.DomainFixtures.VALID_CPF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.DomainFixtures;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerTest {

    @Test
    void registersActive() {
        Customer customer = DomainFixtures.customer();
        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.createdAt()).isEqualTo(NOW);
        assertThatCode(customer::ensureActive).doesNotThrowAnyException();
    }

    @Test
    void cpfAcceptsFormattedAndPlainAndMasksForLogs() {
        assertThat(new Cpf(VALID_CPF)).isEqualTo(new Cpf("52998224725"));
        assertThat(new Cpf(VALID_CPF).masked()).isEqualTo("***.982.***-**");
        assertThat(new Cpf(VALID_CPF).toString()).doesNotContain("52998224725");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "123", "111.111.111-11", "529.982.247-24", "529.982.247-2x", "5299822472500"})
    void cpfRejectsInvalid(String value) {
        assertThatThrownBy(() -> new Cpf(value)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void emailIsNormalizedAndValidated() {
        assertThat(new Email("  Maria@Example.COM ").value()).isEqualTo("maria@example.com");
        assertThatThrownBy(() -> new Email("sem-arroba")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Email("a@b")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void phoneMustBeE164() {
        assertThat(new Phone("+5511999998888")).isNotNull();
        assertThatThrownBy(() -> new Phone("11999998888")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Phone("+55 11 99999-8888")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> Customer.register(" ", new Cpf(VALID_CPF), new Email("a@b.co"),
                new Phone("+5511999998888"), NOW)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void blockedCustomerCanBeReactivatedAndTracksUpdatedAt() {
        Customer customer = DomainFixtures.customer();
        Instant later = NOW.plusSeconds(60);

        customer.block(later);
        assertThat(customer.status()).isEqualTo(CustomerStatus.BLOCKED);
        assertThat(customer.updatedAt()).isEqualTo(later);
        assertThatThrownBy(customer::ensureActive).isInstanceOf(CustomerNotActiveException.class);

        customer.reactivate(later.plusSeconds(60));
        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void closedIsTerminal() {
        Customer customer = DomainFixtures.customer();
        customer.close(NOW);
        assertThatThrownBy(() -> customer.reactivate(NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> customer.block(NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void contactOnlyChangesWhileActive() {
        Customer customer = DomainFixtures.customer();
        customer.updateContact(new Email("novo@example.com"), new Phone("+5521988887777"), NOW);
        assertThat(customer.email().value()).isEqualTo("novo@example.com");

        customer.suspend(NOW);
        assertThatThrownBy(() -> customer.updateContact(new Email("x@example.com"), new Phone("+5521988887777"), NOW))
                .isInstanceOf(CustomerNotActiveException.class);
    }
}

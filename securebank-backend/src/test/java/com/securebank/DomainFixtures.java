package com.securebank;

import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountNumber;
import com.securebank.account.domain.AccountType;
import com.securebank.account.domain.Branch;
import com.securebank.customer.domain.Cpf;
import com.securebank.customer.domain.Customer;
import com.securebank.customer.domain.Email;
import com.securebank.customer.domain.Phone;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/** Construtores de objetos de domínio válidos para os testes. */
public final class DomainFixtures {

    public static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    public static final String VALID_CPF = "529.982.247-25";
    public static final Currency USD = Currency.getInstance("USD");

    private DomainFixtures() {}

    public static Customer customer() {
        return Customer.register("Maria Silva", new Cpf(VALID_CPF), new Email("maria@example.com"),
                new Phone("+5511999998888"), NOW);
    }

    /** Conta BRL ativa com o saldo informado (ex.: "1000.00"). */
    public static Account account(String balance) {
        return account(Money.BRL, balance);
    }

    public static Account account(Currency currency, String balance) {
        Account account = Account.open(CustomerId.newId(), new AccountNumber("123456-7"), new Branch("0001"),
                AccountType.CHECKING, currency, NOW);
        Money amount = new Money(new java.math.BigDecimal(balance), currency);
        if (amount.isPositive()) {
            account.deposit(amount, "seed", NOW);
        }
        return account;
    }

    public static IdempotencyKey key() {
        return new IdempotencyKey("idem-" + UUID.randomUUID());
    }
}

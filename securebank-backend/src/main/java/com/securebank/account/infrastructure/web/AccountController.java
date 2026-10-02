package com.securebank.account.infrastructure.web;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountType;
import com.securebank.limit.application.LimitUsage;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.infrastructure.web.CurrentUser;
import com.securebank.shared.infrastructure.web.MoneyResponse;
import com.securebank.transaction.domain.Transaction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

    record OpenAccountRequest(@NotNull AccountType type) {}

    /** Valor monetário recebido como número ou string ("100.00"); duas casas no máximo. */
    record AmountRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {}

    record AccountResponse(UUID id, String branch, String accountNumber, String type, String status,
            MoneyResponse balance, Instant createdAt) {

        static AccountResponse of(Account a) {
            return new AccountResponse(a.id().value(), a.branch().value(), a.accountNumber().value(),
                    a.type().name(), a.status().name(), MoneyResponse.of(a.balance()), a.createdAt());
        }
    }

    record BalanceResponse(UUID accountId, MoneyResponse balance, Instant asOf) {}

    record TransactionResponse(UUID id, UUID accountId, String type, String direction, MoneyResponse amount,
            MoneyResponse balanceAfter, String status, String reference, Instant createdAt) {

        static TransactionResponse of(Transaction t) {
            return new TransactionResponse(t.id().value(), t.accountId().value(), t.type().name(),
                    t.direction().name(), MoneyResponse.of(t.amount()), MoneyResponse.of(t.balanceAfter()),
                    t.status().name(), t.reference(), t.createdAt());
        }
    }

    record StatementResponse(List<TransactionResponse> items, int page, int size, long totalElements) {}

    record LimitResponse(String type, MoneyResponse perOperation, MoneyResponse daily, MoneyResponse usedToday,
            MoneyResponse remainingToday) {

        static LimitResponse of(LimitUsage.Status s) {
            return new LimitResponse(s.limit().type().name(), MoneyResponse.of(s.limit().perOperation()),
                    MoneyResponse.of(s.limit().daily()), MoneyResponse.of(s.usedToday()),
                    MoneyResponse.of(s.remainingToday()));
        }
    }

    private final AccountApplicationService accounts;
    private final CurrentUser current;

    AccountController(AccountApplicationService accounts, CurrentUser current) {
        this.accounts = accounts;
        this.current = current;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse open(@Valid @RequestBody OpenAccountRequest request) {
        return AccountResponse.of(accounts.open(current.customerId(), request.type()));
    }

    @GetMapping
    List<AccountResponse> list() {
        return accounts.list(current.customerId()).stream().map(AccountResponse::of).toList();
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable String id) {
        return AccountResponse.of(accounts.findOwned(current.customerId(), AccountId.of(id)));
    }

    @GetMapping("/{id}/balance")
    BalanceResponse balance(@PathVariable String id) {
        Account account = accounts.findOwned(current.customerId(), AccountId.of(id));
        return new BalanceResponse(account.id().value(), MoneyResponse.of(account.balance()), Instant.now());
    }

    @GetMapping("/{id}/statement")
    StatementResponse statement(@PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = accounts.statement(current.customerId(), AccountId.of(id), from, to, page, size);
        return new StatementResponse(result.items().stream().map(TransactionResponse::of).toList(), result.page(),
                result.size(), result.totalElements());
    }

    @GetMapping("/{id}/limits")
    List<LimitResponse> limits(@PathVariable String id) {
        return accounts.limits(current.customerId(), AccountId.of(id)).stream().map(LimitResponse::of).toList();
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse deposit(@PathVariable String id, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        return TransactionResponse.of(accounts.deposit(current.customerId(), AccountId.of(id), request.amount()));
    }

    @PostMapping("/{id}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse withdraw(@PathVariable String id, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        return TransactionResponse.of(accounts.withdraw(current.customerId(), AccountId.of(id), request.amount()));
    }
}

package com.securebank.admin.infrastructure.web;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.domain.Account;
import com.securebank.authentication.application.UserAdminApplicationService;
import com.securebank.authentication.domain.User;
import com.securebank.authorization.domain.Role;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.customer.domain.Customer;
import com.securebank.limit.application.LimitApplicationService;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.UserId;
import com.securebank.shared.infrastructure.web.MoneyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operações da equipe. Cada rota exige uma permissão específica (ver SecurityConfig): SUPPORT enxerga, ADMIN altera.
 * Toda alteração gera evento de auditoria nos casos de uso.
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController {

    record CustomerView(UUID id, String name, String document, String email, String phone, String status,
            Instant createdAt) {

        static CustomerView of(Customer c) {
            return new CustomerView(c.id().value(), c.name(), c.document().masked(), c.email().value(),
                    c.phone().value(), c.status().name(), c.createdAt());
        }
    }

    record LimitRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal perOperation,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal daily) {}

    record LimitView(String type, MoneyResponse perOperation, MoneyResponse daily) {

        static LimitView of(Limit l) {
            return new LimitView(l.type().name(), MoneyResponse.of(l.perOperation()), MoneyResponse.of(l.daily()));
        }
    }

    record AccountStatusView(UUID id, String status) {

        static AccountStatusView of(Account a) {
            return new AccountStatusView(a.id().value(), a.status().name());
        }
    }

    record CreateUserRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password,
            @NotNull Role role) {}

    record UserView(UUID id, String email, String role, String status) {

        static UserView of(User u) {
            return new UserView(u.id().value(), u.email().value(), u.role().name(), u.status().name());
        }
    }

    private final CustomerApplicationService customers;
    private final AccountApplicationService accounts;
    private final LimitApplicationService limits;
    private final UserAdminApplicationService users;

    AdminController(CustomerApplicationService customers, AccountApplicationService accounts,
            LimitApplicationService limits, UserAdminApplicationService users) {
        this.customers = customers;
        this.accounts = accounts;
        this.limits = limits;
        this.users = users;
    }

    @GetMapping("/customers/{id}")
    CustomerView customer(@PathVariable String id) {
        return CustomerView.of(customers.get(CustomerId.of(id)));
    }

    @PutMapping("/accounts/{accountId}/limits/{type}")
    LimitView changeLimit(@PathVariable String accountId, @PathVariable String type,
            @Valid @RequestBody LimitRequest request) {
        return LimitView.of(limits.change(AccountId.of(accountId), limitType(type), request.perOperation(),
                request.daily()));
    }

    @PostMapping("/accounts/{id}/block")
    AccountStatusView block(@PathVariable String id) {
        return AccountStatusView.of(accounts.block(AccountId.of(id)));
    }

    @PostMapping("/accounts/{id}/unblock")
    AccountStatusView unblock(@PathVariable String id) {
        return AccountStatusView.of(accounts.unblock(AccountId.of(id)));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    UserView createUser(@Valid @RequestBody CreateUserRequest request) {
        return UserView.of(users.createStaff(request.email(), request.password(), request.role()));
    }

    @PostMapping("/users/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disableUser(@PathVariable String id) {
        users.disable(UserId.of(id));
    }

    @PostMapping("/users/{id}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void enableUser(@PathVariable String id) {
        users.enable(UserId.of(id));
    }

    private static LimitType limitType(String value) {
        try {
            return LimitType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("Limit type must be WITHDRAW, TRANSFER or PAYMENT");
        }
    }
}

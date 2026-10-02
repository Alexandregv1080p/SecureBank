package com.securebank.customer.infrastructure.web;

import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.customer.domain.Customer;
import com.securebank.shared.infrastructure.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
class CustomerController {

    record UpdateRequest(@Size(max = 254) String email, @Size(max = 16) String phone) {}

    /** CPF sempre mascarado na saída: o dado completo não precisa voltar ao cliente nem aos logs. */
    record CustomerResponse(UUID id, String name, String document, String email, String phone, String status,
            Instant createdAt) {

        static CustomerResponse of(Customer c) {
            return new CustomerResponse(c.id().value(), c.name(), c.document().masked(), c.email().value(),
                    c.phone().value(), c.status().name(), c.createdAt());
        }
    }

    private final CustomerApplicationService customers;
    private final CurrentUser current;

    CustomerController(CustomerApplicationService customers, CurrentUser current) {
        this.customers = customers;
        this.current = current;
    }

    @GetMapping("/me")
    CustomerResponse me() {
        return CustomerResponse.of(customers.get(current.customerId()));
    }

    @PatchMapping("/me")
    CustomerResponse updateMe(@Valid @RequestBody UpdateRequest request) {
        return CustomerResponse.of(customers.updateContact(current.customerId(), request.email(), request.phone()));
    }
}

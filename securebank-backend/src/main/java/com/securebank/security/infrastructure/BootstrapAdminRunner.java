package com.securebank.security.infrastructure;

import com.securebank.authentication.application.UserAdminApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Cria o primeiro ADMIN a partir do ambiente (BOOTSTRAP_ADMIN_EMAIL/PASSWORD). Sem a configuração, não faz nada. */
@Component
class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserAdminApplicationService admins;
    private final String email;
    private final String password;

    BootstrapAdminRunner(UserAdminApplicationService admins,
            @Value("${securebank.bootstrap.admin-email:}") String email,
            @Value("${securebank.bootstrap.admin-password:}") String password) {
        this.admins = admins;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        // A senha precisa passar na política: um bootstrap com senha fraca derruba a inicialização.
        if (admins.bootstrapAdmin(email, password)) {
            log.info("Usuário ADMIN inicial criado: {}", email);
        }
    }
}

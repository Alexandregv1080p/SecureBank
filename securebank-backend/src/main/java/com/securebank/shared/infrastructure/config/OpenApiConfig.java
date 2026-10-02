package com.securebank.shared.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("SecureBank Core Banking API").version("v1")
                        .description("Autenticação (JWT, refresh com rotação, MFA TOTP), clientes, contas, depósitos, saques, "
                                + "transferências, pagamentos, extrato, limites, auditoria e administração. "
                                + "Valores monetários trafegam como string (\"100.00\"). Transferências e pagamentos "
                                + "exigem o header Idempotency-Key."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Access token (RS256, 15 min) obtido em POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}

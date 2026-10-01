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

    private static final String DEV_IDENTITY = "devIdentity";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("SecureBank Core Banking API").version("v1")
                        .description("Clientes, contas, depósitos, saques, transferências, pagamentos, extrato e limites. "
                                + "Valores monetários trafegam como string (\"100.00\"). Transferências e pagamentos "
                                + "exigem o header Idempotency-Key."))
                // PROVISÓRIO até a Fase 4: a identidade é um header forjável; vira Bearer JWT.
                .components(new Components().addSecuritySchemes(DEV_IDENTITY, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Customer-Id")
                        .description("Id do cliente (UUID). Apenas desenvolvimento; substituído por JWT na Fase 4.")))
                .addSecurityItem(new SecurityRequirement().addList(DEV_IDENTITY));
    }
}

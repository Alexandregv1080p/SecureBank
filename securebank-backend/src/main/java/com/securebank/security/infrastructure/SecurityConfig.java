package com.securebank.security.infrastructure;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import com.securebank.authorization.domain.Permission;
import com.securebank.authorization.domain.Role;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.IdempotencyStore;
import com.securebank.shared.application.RateLimiter;
import com.securebank.shared.infrastructure.web.ErrorWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Regras de acesso em um só lugar e NEGAR POR PADRÃO: toda rota nova nasce fechada até ganhar uma permissão aqui.
 * API stateless com Bearer token no header: sem cookie de sessão não há CSRF a explorar, então o CSRF fica desligado.
 * Sem CORS configurado, outras origens não passam (o front fala com a API pela mesma origem, via proxy).
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder accessTokenDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter, SecurityHandlers handlers, RateLimiter limiter,
            RateLimitProperties rateLimit, ErrorWriter errors, IdempotencyStore idempotency,
            BankTime time) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        // públicos
                        .requestMatchers(GET, "/api/v1/actuator/health/**", "/api/v1/actuator/info",
                                "/api/v1/actuator/prometheus").permitAll()
                        .requestMatchers(GET, "/.well-known/jwks.json").permitAll()
                        .requestMatchers(POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/mfa/verify").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // qualquer usuário autenticado gerencia a própria sessão, senha e MFA
                        .requestMatchers(POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/security/**").authenticated()
                        // cliente
                        .requestMatchers(GET, "/api/v1/customers/me").hasAuthority(Permission.MANAGE_PROFILE.name())
                        .requestMatchers(PATCH, "/api/v1/customers/me").hasAuthority(Permission.MANAGE_PROFILE.name())
                        .requestMatchers(POST, "/api/v1/accounts").hasAuthority(Permission.OPEN_ACCOUNT.name())
                        .requestMatchers(GET, "/api/v1/accounts/*/statement").hasAuthority(Permission.VIEW_STATEMENT.name())
                        .requestMatchers(GET, "/api/v1/accounts", "/api/v1/accounts/*", "/api/v1/accounts/*/balance",
                                "/api/v1/accounts/*/limits").hasAuthority(Permission.VIEW_ACCOUNT.name())
                        .requestMatchers(POST, "/api/v1/accounts/*/deposits").hasAuthority(Permission.DEPOSIT.name())
                        .requestMatchers(POST, "/api/v1/accounts/*/withdrawals").hasAuthority(Permission.WITHDRAW.name())
                        .requestMatchers(POST, "/api/v1/transfers").hasAuthority(Permission.CREATE_TRANSFER.name())
                        .requestMatchers(GET, "/api/v1/transfers", "/api/v1/transfers/*").hasAuthority(Permission.VIEW_TRANSFER.name())
                        .requestMatchers(POST, "/api/v1/payments").hasAuthority(Permission.CREATE_PAYMENT.name())
                        .requestMatchers(GET, "/api/v1/payments", "/api/v1/payments/*").hasAuthority(Permission.VIEW_PAYMENT.name())
                        .requestMatchers(GET, "/api/v1/piggies", "/api/v1/piggies/*").hasAuthority(Permission.VIEW_PIGGY.name())
                        .requestMatchers(POST, "/api/v1/piggies", "/api/v1/piggies/*/deposits", "/api/v1/piggies/*/withdrawals")
                                .hasAuthority(Permission.MANAGE_PIGGY.name())
                        .requestMatchers(PATCH, "/api/v1/piggies/*").hasAuthority(Permission.MANAGE_PIGGY.name())
                        .requestMatchers(DELETE, "/api/v1/piggies/*").hasAuthority(Permission.MANAGE_PIGGY.name())
                        .requestMatchers(GET, "/api/v1/notifications").hasAuthority(Permission.VIEW_NOTIFICATIONS.name())
                        .requestMatchers(POST, "/api/v1/notifications/*/read").hasAuthority(Permission.VIEW_NOTIFICATIONS.name())
                        // equipe
                        .requestMatchers(GET, "/api/v1/admin/customers/*").hasAuthority(Permission.VIEW_CUSTOMER.name())
                        .requestMatchers(PUT, "/api/v1/admin/accounts/*/limits/*").hasAuthority(Permission.MANAGE_LIMITS.name())
                        .requestMatchers(POST, "/api/v1/admin/accounts/*/block", "/api/v1/admin/accounts/*/unblock")
                                .hasAuthority(Permission.MANAGE_ACCOUNTS.name())
                        .requestMatchers(POST, "/api/v1/admin/users", "/api/v1/admin/users/*/disable",
                                "/api/v1/admin/users/*/enable").hasAuthority(Permission.MANAGE_USERS.name())
                        .requestMatchers(GET, "/api/v1/audit").hasAuthority(Permission.VIEW_AUDIT.name())
                        // todo o resto: negado
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(accessTokenDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(handlers).accessDeniedHandler(handlers));

        if (rateLimit.enabled()) {
            http.addFilterAfter(new RateLimitFilter(RateLimitFilter.rulesFrom(rateLimit), limiter, errors),
                    BearerTokenAuthenticationFilter.class);
        }
        // depois da autorização: só quem pode executar a operação reserva uma Idempotency-Key
        http.addFilterAfter(new IdempotencyFilter(idempotency, errors, time),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        return http.build();
    }

    /** Papel do token → autoridades: ROLE_x e as permissões do papel (resolvidas aqui, não gravadas no token). */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                for (String name : roles) {
                    try {
                        Role role = Role.valueOf(name);
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
                        role.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
                    } catch (IllegalArgumentException ignored) {
                        // papel desconhecido não concede nada
                    }
                }
            }
            return authorities;
        });
        return converter;
    }
}

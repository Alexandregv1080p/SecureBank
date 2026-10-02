package com.securebank.authentication.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.securebank.authentication.application.AuthApplicationService;
import com.securebank.authentication.application.LoginResult;
import com.securebank.authentication.application.SecurityApplicationService;
import com.securebank.authentication.application.TokenPair;
import com.securebank.authentication.domain.User;
import com.securebank.shared.infrastructure.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import java.time.Duration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 14) String document,
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 16) String phone,
            @NotBlank @Size(max = 128) String password) {}

    record RegisterResponse(UUID userId, UUID customerId, String email) {}

    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}

    record MfaVerifyRequest(@NotBlank @Size(max = 2000) String mfaToken, @NotBlank @Size(min = 6, max = 6) String code) {}

    record RefreshRequest(@Size(max = 200) String refreshToken) {}

    /** Com MFA ligado o login devolve só {@code mfaRequired} + {@code mfaToken}; tokens de sessão vêm no /mfa/verify. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TokenResponse(boolean mfaRequired, String mfaToken, String accessToken, String refreshToken,
            String tokenType, Long expiresIn) {

        /** Cliente web: o refresh token vai só no cookie HttpOnly, nunca no corpo (JavaScript não o enxerga). */
        static TokenResponse web(TokenPair pair) {
            return new TokenResponse(false, null, pair.accessToken(), null, "Bearer", pair.expiresInSeconds());
        }

        static TokenResponse of(TokenPair pair) {
            return new TokenResponse(false, null, pair.accessToken(), pair.refreshToken(), "Bearer",
                    pair.expiresInSeconds());
        }

        static TokenResponse of(LoginResult result) {
            return switch (result) {
                case LoginResult.Authenticated authenticated -> of(authenticated.tokens());
                case LoginResult.MfaRequired mfa -> new TokenResponse(true, mfa.mfaToken(), null, null, null,
                        mfa.expiresInSeconds());
            };
        }
    }

    /** Cookie do refresh token: HttpOnly, SameSite=Strict e restrito às rotas de auth. */
    static final String REFRESH_COOKIE = "refresh_token";
    private static final String WEB_CLIENT_HEADER = "X-Client";
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(7);

    private final AuthApplicationService auth;
    private final SecurityApplicationService security;
    private final CurrentUser current;

    AuthController(AuthApplicationService auth, SecurityApplicationService security, CurrentUser current) {
        this.auth = auth;
        this.security = security;
        this.current = current;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = auth.register(request.name(), request.document(), request.email(), request.phone(),
                request.password());
        return new RegisterResponse(user.id().value(), user.customerId().value(), user.email().value());
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        return respond(auth.login(request.email(), request.password()), http, response);
    }

    @PostMapping("/mfa/verify")
    TokenResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        return respond(new LoginResult.Authenticated(auth.verifyMfa(request.mfaToken(), request.code())), http, response);
    }

    /**
     * API: refresh token no corpo. Navegador (header {@code X-Client: web}): lido do cookie. Esse header
     * personalizado não pode ser enviado por outra origem sem preflight CORS (que não existe), o que, somado ao
     * SameSite=Strict, impede CSRF no refresh.
     */
    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody(required = false) RefreshRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        String token = request != null && request.refreshToken() != null ? request.refreshToken() : null;
        if (token == null && isWeb(http) && http.getCookies() != null) {
            for (var cookie : http.getCookies()) {
                if (REFRESH_COOKIE.equals(cookie.getName())) {
                    token = cookie.getValue();
                }
            }
        }
        if (token == null || token.isBlank()) {
            throw com.securebank.shared.application.ApplicationException.unauthenticated("INVALID_REFRESH_TOKEN",
                    "Invalid refresh token");
        }
        try {
            return respond(new LoginResult.Authenticated(auth.refresh(token)), http, response);
        } catch (com.securebank.shared.application.ApplicationException e) {
            if (isWeb(http)) {
                clearCookie(http, response); // token inválido/reusado: não deixa o cookie morto no navegador
            }
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest http, HttpServletResponse response) {
        security.logout(current.userId(), current.sessionId());
        clearCookie(http, response);
    }

    private TokenResponse respond(LoginResult result, HttpServletRequest http, HttpServletResponse response) {
        if (isWeb(http) && result instanceof LoginResult.Authenticated authenticated) {
            TokenPair pair = authenticated.tokens();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(http, pair.refreshToken(), REFRESH_COOKIE_MAX_AGE).toString());
            return TokenResponse.web(pair);
        }
        return TokenResponse.of(result);
    }

    private static boolean isWeb(HttpServletRequest http) {
        return "web".equals(http.getHeader(WEB_CLIENT_HEADER));
    }

    private static void clearCookie(HttpServletRequest http, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(http, "", Duration.ZERO).toString());
    }

    private static ResponseCookie cookie(HttpServletRequest http, String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(http.isSecure()).sameSite("Strict")
                .path("/api/v1/auth").maxAge(maxAge).build();
    }
}

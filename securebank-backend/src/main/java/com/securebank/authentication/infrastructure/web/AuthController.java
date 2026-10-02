package com.securebank.authentication.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.securebank.authentication.application.AuthApplicationService;
import com.securebank.authentication.application.LoginResult;
import com.securebank.authentication.application.SecurityApplicationService;
import com.securebank.authentication.application.TokenPair;
import com.securebank.authentication.domain.User;
import com.securebank.shared.infrastructure.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

    record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    /** Com MFA ligado o login devolve só {@code mfaRequired} + {@code mfaToken}; tokens de sessão vêm no /mfa/verify. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TokenResponse(boolean mfaRequired, String mfaToken, String accessToken, String refreshToken,
            String tokenType, Long expiresIn) {

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
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.of(auth.login(request.email(), request.password()));
    }

    @PostMapping("/mfa/verify")
    TokenResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        return TokenResponse.of(auth.verifyMfa(request.mfaToken(), request.code()));
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.of(auth.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout() {
        security.logout(current.userId(), current.sessionId());
    }
}

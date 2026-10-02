package com.securebank.authentication.application;

import com.securebank.authentication.domain.Session;
import com.securebank.authentication.domain.User;
import com.securebank.shared.domain.UserId;

public interface TokenIssuer {

    record AccessToken(String value, long expiresInSeconds) {}

    AccessToken issueAccessToken(User user, Session session);

    /** Token curto, de audiência própria (não vale como access token), entregue entre senha e código MFA. */
    String issueMfaChallenge(UserId userId);

    /** @throws com.securebank.shared.application.ApplicationException 401 se inválido, expirado ou de outra audiência */
    UserId parseMfaChallenge(String token);
}

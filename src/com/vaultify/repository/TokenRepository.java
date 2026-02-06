package com.vaultify.repository;

import java.util.List;
import com.vaultify.models.Token;

/**
 * TokenRepository abstraction for share token lifecycle management.
 */
public interface TokenRepository {
    Token save(Token token);

    Token findByTokenString(String tokenString);

    List<Token> findByIssuerUserId(long userId);

    void revoke(String tokenString);

    void deleteExpired();
}

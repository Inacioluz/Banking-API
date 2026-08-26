package com.inacio.banking.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Whitelist de refresh tokens no Redis. Permite revogar sessoes no logout e
 * rotacionar o token a cada renovacao — um refresh usado duas vezes falha.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final String USER_KEY_PREFIX = "auth:user-sessions:";

    private final StringRedisTemplate redisTemplate;

    public void store(String tokenId, UUID userId, Duration ttl) {
        redisTemplate.opsForValue().set(tokenKey(tokenId), userId.toString(), ttl);
        redisTemplate.opsForSet().add(userKey(userId), tokenId);
        redisTemplate.expire(userKey(userId), ttl);
    }

    public boolean isActive(String tokenId, UUID userId) {
        String storedUserId = redisTemplate.opsForValue().get(tokenKey(tokenId));
        return storedUserId != null && storedUserId.equals(userId.toString());
    }

    public void revoke(String tokenId, UUID userId) {
        redisTemplate.delete(tokenKey(tokenId));
        redisTemplate.opsForSet().remove(userKey(userId), tokenId);
    }

    /** Encerra todas as sessoes ativas do usuario. */
    public void revokeAll(UUID userId) {
        Set<String> tokenIds = redisTemplate.opsForSet().members(userKey(userId));
        if (tokenIds != null && !tokenIds.isEmpty()) {
            redisTemplate.delete(tokenIds.stream().map(RefreshTokenStore::tokenKey).toList());
        }
        redisTemplate.delete(userKey(userId));
    }

    private static String tokenKey(String tokenId) {
        return TOKEN_KEY_PREFIX + tokenId;
    }

    private static String userKey(UUID userId) {
        return USER_KEY_PREFIX + userId;
    }
}

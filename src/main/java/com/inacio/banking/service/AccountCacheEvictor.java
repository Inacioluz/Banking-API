package com.inacio.banking.service;

import com.inacio.banking.config.CacheConfig;
import com.inacio.banking.domain.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Invalidacao programatica do cache. Uma transferencia afeta duas contas e dois
 * titulares, o que nao cabe bem nas chaves estaticas de {@code @CacheEvict}.
 */
@Component
@RequiredArgsConstructor
public class AccountCacheEvictor {

    private final CacheManager cacheManager;

    public void evict(Account account) {
        evict(account.getId(), account.getOwner().getId());
    }

    public void evict(UUID accountId, UUID ownerId) {
        evictKey(CacheConfig.ACCOUNTS_CACHE, accountId);
        evictKey(CacheConfig.BALANCES_CACHE, accountId);
        evictKey(CacheConfig.ACCOUNTS_BY_OWNER_CACHE, ownerId);
    }

    private void evictKey(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
}

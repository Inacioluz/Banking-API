package com.inacio.banking.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inacio.banking.dto.account.AccountResponse;
import com.inacio.banking.dto.account.BalanceResponse;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cache distribuido no Redis. Cada cache declara o proprio TTL e um serializador
 * JSON tipado, de modo que os valores possam ser inspecionados via redis-cli
 * sem metadados de classe.
 */
@Configuration
public class CacheConfig {

    /** Detalhes da conta, invalidado a cada movimentacao. */
    public static final String ACCOUNTS_CACHE = "accounts";
    /** Saldo consultado com frequencia, TTL curto. */
    public static final String BALANCES_CACHE = "balances";
    /** Lista de contas por titular. */
    public static final String ACCOUNTS_BY_OWNER_CACHE = "accountsByOwner";

    @Bean
    public RedisCacheConfiguration defaultCacheConfiguration() {
        return baseConfiguration().entryTtl(Duration.ofMinutes(10));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheCustomizer(ObjectMapper objectMapper) {
        ObjectMapper cacheMapper = objectMapper.copy();

        JavaType accountType = cacheMapper.getTypeFactory().constructType(AccountResponse.class);
        JavaType balanceType = cacheMapper.getTypeFactory().constructType(BalanceResponse.class);
        JavaType accountListType = cacheMapper.getTypeFactory()
                .constructCollectionType(List.class, AccountResponse.class);

        return builder -> builder.withInitialCacheConfigurations(Map.of(
                ACCOUNTS_CACHE, typedConfiguration(cacheMapper, accountType, Duration.ofMinutes(10)),
                BALANCES_CACHE, typedConfiguration(cacheMapper, balanceType, Duration.ofSeconds(30)),
                ACCOUNTS_BY_OWNER_CACHE, typedConfiguration(cacheMapper, accountListType, Duration.ofMinutes(5))));
    }

    private RedisCacheConfiguration typedConfiguration(ObjectMapper mapper, JavaType type, Duration ttl) {
        return baseConfiguration()
                .entryTtl(ttl)
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(mapper, type)));
    }

    private RedisCacheConfiguration baseConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .prefixCacheNameWith("banking:")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));
    }
}

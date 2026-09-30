package org.herb.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
public class SessionService {
    private static final Duration TTL = Duration.ofHours(1);

    @Autowired
    private StringRedisTemplate redis;

    private String key(Integer userId) {
        return "user:sessions:" + userId;
    }

    public void register(Integer userId, String token) {
        redis.opsForValue().set(token, token, TTL);
        redis.opsForSet().add(key(userId), token);
        redis.expire(key(userId), TTL);
    }

    public void revoke(Integer userId, String token) {
        redis.delete(token);
        redis.opsForSet().remove(key(userId), token);
    }

    public void revokeAll(Integer userId) {
        Set<String> tokens = redis.opsForSet().members(key(userId));
        if (tokens != null && !tokens.isEmpty()) redis.delete(tokens);
        redis.delete(key(userId));
    }
}

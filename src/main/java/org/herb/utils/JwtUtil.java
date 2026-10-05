package org.herb.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.util.Date;
import java.util.Map;

public final class JwtUtil {
    private JwtUtil() {}

    private static Algorithm algorithm() {
        String key = org.herb.config.LocalEnvironment.get("HERB_JWT_SECRET");
        if (key == null || key.length() < 32) {
            throw new IllegalStateException("HERB_JWT_SECRET must have at least 32 characters");
        }
        return Algorithm.HMAC256(key);
    }

    public static String genToken(Map<String, Object> claims) {
        return JWT.create()
                .withClaim("claims", claims)
                .withExpiresAt(new Date(System.currentTimeMillis() + 1000L * 60 * 60))
                .sign(algorithm());
    }

    public static Map<String, Object> parseToken(String token) {
        return JWT.require(algorithm())
                .build()
                .verify(token)
                .getClaim("claims")
                .asMap();
    }
}

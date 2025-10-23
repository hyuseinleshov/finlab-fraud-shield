package com.finlab.gateway.service;

import com.finlab.gateway.repository.JwtTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Stateful JWT service with dual storage (Redis + database) for instant revocation.
 * Stores tokens in both Redis (sub-1ms lookups) and database (persistence across restarts).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String REDIS_TOKEN_PREFIX = "jwt:token:";
    private static final String REDIS_BLACKLIST_PREFIX = "jwt:blacklist:";

    private final String jwtSecret;
    private final long jwtExpirationMs;
    private final long jwtRefreshExpirationMs;
    private final RedisTemplate<String, String> redisTemplate;
    private final JwtTokenRepository jwtTokenRepository;

    public JwtService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration-ms:900000}") long jwtExpirationMs,
            @Value("${jwt.refresh-expiration-ms:604800000}") long jwtRefreshExpirationMs,
            RedisTemplate<String, String> redisTemplate,
            JwtTokenRepository jwtTokenRepository) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationMs = jwtExpirationMs;
        this.jwtRefreshExpirationMs = jwtRefreshExpirationMs;
        this.redisTemplate = redisTemplate;
        this.jwtTokenRepository = jwtTokenRepository;
    }

    public String generateToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "access");

        return createToken(claims, userId, jwtExpirationMs);
    }

    public String generateRefreshToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");

        return createToken(claims, userId, jwtRefreshExpirationMs);
    }

    private String createToken(Map<String, Object> claims, String subject, long expirationMs) {
        Instant now = Instant.now();
        Instant expiration = now.plusMillis(expirationMs);

        String token = Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(getSigningKey())
                .compact();

        String tokenType = (String) claims.get("type");
        String redisKey = REDIS_TOKEN_PREFIX + token;
        redisTemplate.opsForValue().set(redisKey, subject, Duration.ofMillis(expirationMs));
        jwtTokenRepository.saveToken(subject, token, expiration, tokenType);

        log.debug("Generated {} token for user: {}, expires at: {}",
                  tokenType, subject, expiration);

        return token;
    }

    /**
     * Validates token using layered approach: blacklist check → Redis → database.
     *
     * @param token JWT token to validate
     * @return true if token is valid and not blacklisted, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            if (isTokenBlacklisted(token)) {
                log.debug("Token is blacklisted");
                return false;
            }

            Claims claims = extractAllClaims(token);

            String redisKey = REDIS_TOKEN_PREFIX + token;
            String cachedUserId = redisTemplate.opsForValue().get(redisKey);

            if (cachedUserId != null) {
                log.debug("Token validated from Redis cache");
                return true;
            }

            String userId = claims.getSubject();
            boolean existsInDb = jwtTokenRepository.tokenExists(userId, token);

            if (existsInDb) {
                log.debug("Token validated from database (Redis miss)");
                long remainingTtl = claims.getExpiration().getTime() - System.currentTimeMillis();
                if (remainingTtl > 0) {
                    redisTemplate.opsForValue().set(redisKey, userId, Duration.ofMillis(remainingTtl));
                }
                return true;
            }

            log.debug("Token not found in Redis or database");
            return false;

        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.error("JWT token is malformed: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.error("JWT signature validation failed: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage(), e);
            return false;
        }
    }

    public String extractUserId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.error("Failed to extract userId from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Invalidates token by blacklisting and removing from storage.
     * Blacklist TTL matches remaining token lifetime to minimize Redis memory usage.
     *
     * @param token JWT token to invalidate
     */
    public void invalidateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String userId = claims.getSubject();
            long remainingTtl = claims.getExpiration().getTime() - System.currentTimeMillis();

            if (remainingTtl > 0) {
                String blacklistKey = REDIS_BLACKLIST_PREFIX + token;
                redisTemplate.opsForValue().set(blacklistKey, "true", Duration.ofMillis(remainingTtl));
                log.debug("Token blacklisted for remaining TTL: {}ms", remainingTtl);
            }

            String redisKey = REDIS_TOKEN_PREFIX + token;
            redisTemplate.delete(redisKey);
            jwtTokenRepository.deleteToken(userId, token);

            log.info("Token invalidated for user: {}", userId);

        } catch (Exception e) {
            log.error("Failed to invalidate token: {}", e.getMessage(), e);
        }
    }

    private boolean isTokenBlacklisted(String token) {
        String blacklistKey = REDIS_BLACKLIST_PREFIX + token;
        return redisTemplate.hasKey(blacklistKey);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            log.error("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }
}

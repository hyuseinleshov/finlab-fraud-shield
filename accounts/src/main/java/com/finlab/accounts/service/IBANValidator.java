package com.finlab.accounts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class IBANValidator {

    private static final Logger log = LoggerFactory.getLogger(IBANValidator.class);

    private static final String BULGARIAN_COUNTRY_CODE = "BG";
    private static final int BULGARIAN_IBAN_LENGTH = 22;
    private static final int VALID_MOD_RESULT = 1;
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String CACHE_KEY_PREFIX = "iban:valid:";

    private final StringRedisTemplate redisTemplate;

    public IBANValidator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public ValidationResult validate(String iban) {
        if (iban == null || iban.isBlank()) {
            return ValidationResult.invalid("IBAN cannot be null or empty");
        }

        String normalizedIban = iban.trim().toUpperCase().replaceAll("\\s+", "");

        Optional<Boolean> cachedResult = checkCache(normalizedIban);
        if (cachedResult.isPresent()) {
            log.debug("Cache hit for IBAN: {}", maskIban(normalizedIban));
            return cachedResult.get()
                ? ValidationResult.valid()
                : ValidationResult.invalid("Invalid IBAN checksum");
        }

        if (!normalizedIban.startsWith(BULGARIAN_COUNTRY_CODE)) {
            return ValidationResult.invalid("IBAN must start with BG");
        }

        if (normalizedIban.length() != BULGARIAN_IBAN_LENGTH) {
            return ValidationResult.invalid(
                String.format("Bulgarian IBAN must be exactly %d characters, got %d",
                    BULGARIAN_IBAN_LENGTH, normalizedIban.length())
            );
        }

        if (!normalizedIban.substring(2, 4).matches("\\d{2}")) {
            return ValidationResult.invalid("Check digits must be numeric");
        }

        if (!normalizedIban.substring(4).matches("[A-Z0-9]+")) {
            return ValidationResult.invalid("IBAN contains invalid characters");
        }

        boolean isValid = validateChecksum(normalizedIban);
        cacheResult(normalizedIban, isValid);

        return isValid
            ? ValidationResult.valid()
            : ValidationResult.invalid("Invalid IBAN checksum");
    }

    private boolean validateChecksum(String iban) {
        try {
            String rearranged = iban.substring(4) + iban.substring(0, 4);

            StringBuilder numericIban = new StringBuilder();
            for (char c : rearranged.toCharArray()) {
                if (Character.isDigit(c)) {
                    numericIban.append(c);
                } else {
                    numericIban.append(c - 'A' + 10);
                }
            }

            int mod = calculateMod97(numericIban.toString());
            return mod == VALID_MOD_RESULT;

        } catch (Exception e) {
            log.error("Error validating IBAN checksum", e);
            return false;
        }
    }

    private int calculateMod97(String numericString) {
        String remainder = "0";
        int chunkSize = 9;

        for (int i = 0; i < numericString.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, numericString.length());
            String chunk = numericString.substring(i, end);
            String combined = remainder + chunk;
            long value = Long.parseLong(combined);
            remainder = String.valueOf(value % 97);
        }

        return Integer.parseInt(remainder);
    }

    private Optional<Boolean> checkCache(String iban) {
        try {
            String cacheKey = CACHE_KEY_PREFIX + iban;
            String cached = redisTemplate.opsForValue().get(cacheKey);
            return Optional.ofNullable(cached).map(Boolean::valueOf);
        } catch (Exception e) {
            log.warn("Redis cache read failed for IBAN validation", e);
            return Optional.empty();
        }
    }

    private void cacheResult(String iban, boolean isValid) {
        try {
            String cacheKey = CACHE_KEY_PREFIX + iban;
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(isValid), CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis cache write failed for IBAN validation", e);
        }
    }

    private String maskIban(String iban) {
        if (iban.length() <= 8) {
            return "****";
        }
        return iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4);
    }

    public record ValidationResult(boolean isValid, String errorMessage) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }
}

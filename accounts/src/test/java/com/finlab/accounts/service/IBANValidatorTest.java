package com.finlab.accounts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IBANValidatorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IBANValidator validator;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        validator = new IBANValidator(redisTemplate);
    }

    @Test
    void validate_WithValidBulgarianIBAN_ShouldReturnTrue() {
        String validIban = "BG80BNBG96611020345678";
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(validIban);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errorMessage()).isNull();
        verify(valueOperations).set(any(), eq("true"), any());
    }

    @Test
    void validate_WithValidBulgarianIBANFromDatabase_ShouldReturnTrue() {
        // Known valid Bulgarian IBANs (verified with MOD 97)
        String[] validIbans = {
            "BG80BNBG96611020345678"
        };

        when(valueOperations.get(any())).thenReturn(null);

        for (String iban : validIbans) {
            IBANValidator.ValidationResult result = validator.validate(iban);
            assertThat(result.isValid())
                .withFailMessage("IBAN %s should be valid", iban)
                .isTrue();
        }
    }

    @Test
    void validate_WithInvalidChecksum_ShouldReturnFalse() {
        // Valid format but wrong checksum
        String invalidIban = "BG00BNBG96611020345678";
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(invalidIban);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("Invalid IBAN checksum");
        verify(valueOperations).set(any(), eq("false"), any());
    }

    @Test
    void validate_WithNullIBAN_ShouldReturnFalse() {
        IBANValidator.ValidationResult result = validator.validate(null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("null or empty");
    }

    @Test
    void validate_WithEmptyIBAN_ShouldReturnFalse() {
        IBANValidator.ValidationResult result = validator.validate("   ");

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("null or empty");
    }

    @Test
    void validate_WithWrongCountryCode_ShouldReturnFalse() {
        // German IBAN
        String germanIban = "DE89370400440532013000";
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(germanIban);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("must start with BG");
    }

    @Test
    void validate_WithWrongLength_ShouldReturnFalse() {
        // Too short
        String shortIban = "BG80BNBG9661102034";
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(shortIban);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("exactly 22 characters");
    }

    @Test
    void validate_WithNonNumericCheckDigits_ShouldReturnFalse() {
        String invalidIban = "BGAABNBG96611020345678";
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(invalidIban);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("Check digits must be numeric");
    }

    @Test
    void validate_WithInvalidCharacters_ShouldReturnFalse() {
        // Contains special characters
        String invalidIban = "BG80BNBG9661102@345678";
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(invalidIban);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("invalid characters");
    }

    @Test
    void validate_WithCachedValidResult_ShouldReturnCachedValue() {
        String iban = "BG80BNBG96611020345678";
        when(valueOperations.get(any())).thenReturn("true");

        IBANValidator.ValidationResult result = validator.validate(iban);

        assertThat(result.isValid()).isTrue();
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    void validate_WithCachedInvalidResult_ShouldReturnCachedValue() {
        String iban = "BG00BNBG96611020345678";
        when(valueOperations.get(any())).thenReturn("false");

        IBANValidator.ValidationResult result = validator.validate(iban);

        assertThat(result.isValid()).isFalse();
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    void validate_WithLowercaseIBAN_ShouldNormalizeAndValidate() {
        String lowercaseIban = "bg80bnbg96611020345678";
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(lowercaseIban);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_WithSpacesInIBAN_ShouldNormalizeAndValidate() {
        String ibanWithSpaces = "BG80 BNBG 9661 1020 3456 78";
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(ibanWithSpaces);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_WhenRedisFails_ShouldStillValidate() {
        String validIban = "BG80BNBG96611020345678";
        when(valueOperations.get(any())).thenThrow(new RuntimeException("Redis connection failed"));
        doThrow(new RuntimeException("Redis connection failed"))
            .when(valueOperations).set(any(), any(), any());

        IBANValidator.ValidationResult result = validator.validate(validIban);

        // Validation still works without cache
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_WithCachingAcrossMultipleValidations_ShouldCacheCorrectly() {
        // Given - same IBAN validated multiple times
        String validIban = "BG80BNBG96611020345678";
        when(valueOperations.get(any())).thenReturn(null);

        // When - first validation (cache miss)
        IBANValidator.ValidationResult firstResult = validator.validate(validIban);

        // Then
        assertThat(firstResult.isValid()).isTrue();
        verify(valueOperations, times(1)).set(any(), eq("true"), any());

        // When - second validation (should use cache)
        when(valueOperations.get(any())).thenReturn("true");
        IBANValidator.ValidationResult secondResult = validator.validate(validIban);

        // Then - no additional cache writes
        assertThat(secondResult.isValid()).isTrue();
        verify(valueOperations, times(1)).set(any(), eq("true"), any());
    }
}

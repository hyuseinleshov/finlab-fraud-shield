package com.finlab.accounts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        // Given
        String validIban = "BG80BNBG96611020345678";
        when(valueOperations.get(any())).thenReturn(null);

        // When
        IBANValidator.ValidationResult result = validator.validate(validIban);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.errorMessage()).isNull();
        verify(valueOperations).set(any(), eq("true"), any());
    }

    @Test
    void validate_WithValidBulgarianIBANFromDatabase_ShouldReturnTrue() {
        // Known valid Bulgarian IBANs (verified with MOD 97)
        // BG80BNBG96611020345678 is a confirmed valid Bulgarian IBAN
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
        // Given - valid format but wrong checksum
        String invalidIban = "BG00BNBG96611020345678";
        when(valueOperations.get(any())).thenReturn(null);

        // When
        IBANValidator.ValidationResult result = validator.validate(invalidIban);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("Invalid IBAN checksum");
        verify(valueOperations).set(any(), eq("false"), any());
    }

    @Test
    void validate_WithNullIBAN_ShouldReturnFalse() {
        // When
        IBANValidator.ValidationResult result = validator.validate(null);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("null or empty");
    }

    @Test
    void validate_WithEmptyIBAN_ShouldReturnFalse() {
        // When
        IBANValidator.ValidationResult result = validator.validate("   ");

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("null or empty");
    }

    @Test
    void validate_WithWrongCountryCode_ShouldReturnFalse() {
        // Given - German IBAN
        String germanIban = "DE89370400440532013000";
        when(valueOperations.get(any())).thenReturn(null);

        // When
        IBANValidator.ValidationResult result = validator.validate(germanIban);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("must start with BG");
    }

    @Test
    void validate_WithWrongLength_ShouldReturnFalse() {
        // Given - too short
        String shortIban = "BG80BNBG9661102034";
        when(valueOperations.get(any())).thenReturn(null);

        // When
        IBANValidator.ValidationResult result = validator.validate(shortIban);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("exactly 22 characters");
    }

    @Test
    void validate_WithNonNumericCheckDigits_ShouldReturnFalse() {
        // Given
        String invalidIban = "BGAABNBG96611020345678";
        when(valueOperations.get(any())).thenReturn(null);

        // When
        IBANValidator.ValidationResult result = validator.validate(invalidIban);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("Check digits must be numeric");
    }

    @Test
    void validate_WithInvalidCharacters_ShouldReturnFalse() {
        // Given - contains special characters
        String invalidIban = "BG80BNBG9661102@345678";
        when(valueOperations.get(any())).thenReturn(null);

        // When
        IBANValidator.ValidationResult result = validator.validate(invalidIban);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("invalid characters");
    }

    @Test
    void validate_WithCachedValidResult_ShouldReturnCachedValue() {
        // Given
        String iban = "BG80BNBG96611020345678";
        when(valueOperations.get(any())).thenReturn("true");

        // When
        IBANValidator.ValidationResult result = validator.validate(iban);

        // Then
        assertThat(result.isValid()).isTrue();
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    void validate_WithCachedInvalidResult_ShouldReturnCachedValue() {
        // Given
        String iban = "BG00BNBG96611020345678";
        when(valueOperations.get(any())).thenReturn("false");

        // When
        IBANValidator.ValidationResult result = validator.validate(iban);

        // Then
        assertThat(result.isValid()).isFalse();
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    void validate_WithLowercaseIBAN_ShouldNormalizeAndValidate() {
        // Given
        String lowercaseIban = "bg80bnbg96611020345678";
        when(valueOperations.get(any())).thenReturn(null);

        // When
        IBANValidator.ValidationResult result = validator.validate(lowercaseIban);

        // Then
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_WithSpacesInIBAN_ShouldNormalizeAndValidate() {
        // Given
        String ibanWithSpaces = "BG80 BNBG 9661 1020 3456 78";
        when(valueOperations.get(any())).thenReturn(null);

        // When
        IBANValidator.ValidationResult result = validator.validate(ibanWithSpaces);

        // Then
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_WhenRedisFails_ShouldStillValidate() {
        // Given
        String validIban = "BG80BNBG96611020345678";
        when(valueOperations.get(any())).thenThrow(new RuntimeException("Redis connection failed"));
        doThrow(new RuntimeException("Redis connection failed"))
            .when(valueOperations).set(any(), any(), any());

        // When
        IBANValidator.ValidationResult result = validator.validate(validIban);

        // Then - validation still works without cache
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

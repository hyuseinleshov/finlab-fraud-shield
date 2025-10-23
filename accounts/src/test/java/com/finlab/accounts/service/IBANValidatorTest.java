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

    // Test IBAN constants
    private static final String VALID_BULGARIAN_IBAN = "BG80BNBG96611020345678";
    private static final String INVALID_CHECKSUM_IBAN = "BG00BNBG96611020345678";
    private static final String GERMAN_IBAN = "DE89370400440532013000";
    private static final String SHORT_IBAN = "BG80BNBG9661102034";
    private static final String INVALID_CHECK_DIGITS_IBAN = "BGAABNBG96611020345678";
    private static final String INVALID_CHARACTERS_IBAN = "BG80BNBG9661102@345678";
    private static final String LOWERCASE_IBAN = "bg80bnbg96611020345678";
    private static final String IBAN_WITH_SPACES = "BG80 BNBG 9661 1020 3456 78";

    // Redis cache values
    private static final String CACHE_VALUE_TRUE = "true";
    private static final String CACHE_VALUE_FALSE = "false";

    // Whitespace constant
    private static final String WHITESPACE_ONLY = "   ";

    // Error message fragments
    private static final String ERROR_NULL_OR_EMPTY = "null or empty";
    private static final String ERROR_INVALID_CHECKSUM = "Invalid IBAN checksum";
    private static final String ERROR_MUST_START_WITH_BG = "must start with BG";
    private static final String ERROR_EXACTLY_22_CHARS = "exactly 22 characters";
    private static final String ERROR_CHECK_DIGITS_NUMERIC = "Check digits must be numeric";
    private static final String ERROR_INVALID_CHARACTERS = "invalid characters";

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
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(VALID_BULGARIAN_IBAN);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errorMessage()).isNull();
        verify(valueOperations).set(any(), eq(CACHE_VALUE_TRUE), any());
    }

    @Test
    void validate_WithValidBulgarianIBANFromDatabase_ShouldReturnTrue() {
        String[] validIbans = {
            VALID_BULGARIAN_IBAN
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
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(INVALID_CHECKSUM_IBAN);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains(ERROR_INVALID_CHECKSUM);
        verify(valueOperations).set(any(), eq(CACHE_VALUE_FALSE), any());
    }

    @Test
    void validate_WithNullIBAN_ShouldReturnFalse() {
        IBANValidator.ValidationResult result = validator.validate(null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains(ERROR_NULL_OR_EMPTY);
    }

    @Test
    void validate_WithEmptyIBAN_ShouldReturnFalse() {
        IBANValidator.ValidationResult result = validator.validate(WHITESPACE_ONLY);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains(ERROR_NULL_OR_EMPTY);
    }

    @Test
    void validate_WithWrongCountryCode_ShouldReturnFalse() {
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(GERMAN_IBAN);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains(ERROR_MUST_START_WITH_BG);
    }

    @Test
    void validate_WithWrongLength_ShouldReturnFalse() {
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(SHORT_IBAN);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains(ERROR_EXACTLY_22_CHARS);
    }

    @Test
    void validate_WithNonNumericCheckDigits_ShouldReturnFalse() {
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(INVALID_CHECK_DIGITS_IBAN);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains(ERROR_CHECK_DIGITS_NUMERIC);
    }

    @Test
    void validate_WithInvalidCharacters_ShouldReturnFalse() {
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(INVALID_CHARACTERS_IBAN);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains(ERROR_INVALID_CHARACTERS);
    }

    @Test
    void validate_WithCachedValidResult_ShouldReturnCachedValue() {
        when(valueOperations.get(any())).thenReturn(CACHE_VALUE_TRUE);

        IBANValidator.ValidationResult result = validator.validate(VALID_BULGARIAN_IBAN);

        assertThat(result.isValid()).isTrue();
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    void validate_WithCachedInvalidResult_ShouldReturnCachedValue() {
        when(valueOperations.get(any())).thenReturn(CACHE_VALUE_FALSE);

        IBANValidator.ValidationResult result = validator.validate(INVALID_CHECKSUM_IBAN);

        assertThat(result.isValid()).isFalse();
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    void validate_WithLowercaseIBAN_ShouldNormalizeAndValidate() {
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(LOWERCASE_IBAN);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_WithSpacesInIBAN_ShouldNormalizeAndValidate() {
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult result = validator.validate(IBAN_WITH_SPACES);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_WhenRedisFails_ShouldStillValidate() {
        when(valueOperations.get(any())).thenThrow(new RuntimeException("Redis connection failed"));
        doThrow(new RuntimeException("Redis connection failed"))
            .when(valueOperations).set(any(), any(), any());

        IBANValidator.ValidationResult result = validator.validate(VALID_BULGARIAN_IBAN);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_WithCachingAcrossMultipleValidations_ShouldCacheCorrectly() {
        when(valueOperations.get(any())).thenReturn(null);

        IBANValidator.ValidationResult firstResult = validator.validate(VALID_BULGARIAN_IBAN);

        assertThat(firstResult.isValid()).isTrue();
        verify(valueOperations, times(1)).set(any(), eq(CACHE_VALUE_TRUE), any());

        when(valueOperations.get(any())).thenReturn(CACHE_VALUE_TRUE);
        IBANValidator.ValidationResult secondResult = validator.validate(VALID_BULGARIAN_IBAN);

        assertThat(secondResult.isValid()).isTrue();
        verify(valueOperations, times(1)).set(any(), eq(CACHE_VALUE_TRUE), any());
    }
}

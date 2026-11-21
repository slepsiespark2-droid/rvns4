package ravens4.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Basic sanity tests for FalconGuard. These verify the class behaves
 * safely in a plain test environment - they don't test the "real"
 * anti-tamper behavior, which requires a built jar with the watched
 * classes present and EXPECTED_HASH set correctly.
 */
class FalconGuardTest {

    @Test
    void startDoesNotThrow() {
        assertDoesNotThrow(FalconGuard::start);
    }

    @Test
    void isValidReturnsWithoutCrashing() {
        FalconGuard.start();
        assertDoesNotThrow(FalconGuard::isValid);
    }

    @Test
    void stopDoesNotThrowWhenNotStarted() {
        assertDoesNotThrow(FalconGuard::stop);
    }
}

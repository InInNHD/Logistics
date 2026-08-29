package com.firefly.warehouse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class CredentialCipherTest {
    @Test
    void encryptsWithRandomIvAndDecryptsWithoutExposingTheSecret() {
        CredentialCipher cipher = new CredentialCipher("test-carrier-key-with-more-than-32-characters", new MockEnvironment());
        String first = cipher.encrypt("carrier-token-123456");
        String second = cipher.encrypt("carrier-token-123456");
        assertNotEquals(first, second);
        assertEquals("carrier-token-123456", cipher.decrypt(first));
        assertEquals("••••3456", CredentialCipher.hint("carrier-token-123456"));
    }
}

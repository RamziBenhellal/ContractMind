package com.ramzi.backend.service.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialEncryptionServiceTest {

    private final CredentialEncryptionService service =
            new CredentialEncryptionService("unit-test-key");

    @Test
    void encryptsAndDecryptsLoginAndPin() {
        String cipher = service.encryptCredentials("1234567", "geheim");

        assertThat(cipher).isNotBlank();
        assertThat(cipher).doesNotContain("1234567");
        assertThat(cipher).doesNotContain("geheim");

        CredentialEncryptionService.CredentialsPayload payload = service.decryptCredentials(cipher);
        assertThat(payload.loginId()).isEqualTo("1234567");
        assertThat(payload.pin()).isEqualTo("geheim");
    }

    @Test
    void differentKeysCannotDecrypt() {
        String cipher = service.encryptCredentials("login", "pin");
        CredentialEncryptionService other = new CredentialEncryptionService("another-key");

        assertThatThrownBy(() -> other.decryptCredentials(cipher))
                .isInstanceOf(IllegalStateException.class);
    }
}

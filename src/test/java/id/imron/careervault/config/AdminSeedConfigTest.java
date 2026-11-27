package id.imron.careervault.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import id.imron.careervault.domain.UserAccount;
import id.imron.careervault.repository.UserAccountRepository;

@ExtendWith(OutputCaptureExtension.class)
class AdminSeedConfigTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void createsAdminInAnEmptyTableWithoutLoggingThePassword(CapturedOutput output) throws Exception {
        seed("owner", "s3cret-never-logged");

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("owner");
        assertThat(saved.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(encoder.matches("s3cret-never-logged", saved.getValue().getPasswordHash())).isTrue();
        assertThat(output).contains("initial admin account")
                .doesNotContain("s3cret-never-logged")
                .doesNotContain(saved.getValue().getPasswordHash());
    }

    @Test
    void doesNothingWhenAnAccountExists() throws Exception {
        when(users.count()).thenReturn(1L);

        seed("owner", "password");

        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"owner, ''", "owner, '   '", "'', password"})
    void skipsWhenCredentialsAreBlank(String username, String password, CapturedOutput output) throws Exception {
        seed(username, password);

        verify(users, never()).save(any());
        assertThat(output).contains("No admin account created");
    }

    private void seed(String username, String password) throws Exception {
        new AdminSeedConfig().adminSeeder(users, encoder, username, password).run(null);
    }
}

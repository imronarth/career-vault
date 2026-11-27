package id.imron.careervault.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import id.imron.careervault.domain.UserAccount;
import id.imron.careervault.repository.UserAccountRepository;

/** Creates the first admin account when there is no account at all. */
@Configuration(proxyBeanMethods = false)
public class AdminSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedConfig.class);

    @Bean
    ApplicationRunner adminSeeder(UserAccountRepository users, PasswordEncoder passwordEncoder,
            @Value("${APP_ADMIN_USERNAME:admin}") String username,
            @Value("${APP_ADMIN_PASSWORD:}") String password) {
        return args -> {
            if (users.count() > 0) {
                return;
            }
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                log.warn("No admin account created: set APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD.");
                return;
            }
            UserAccount admin = new UserAccount();
            admin.setUsername(username.strip());
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setFullName("Administrator");
            admin.setRole("ADMIN");
            users.save(admin);
            log.info("Created the initial admin account '{}'.", admin.getUsername());
        };
    }
}

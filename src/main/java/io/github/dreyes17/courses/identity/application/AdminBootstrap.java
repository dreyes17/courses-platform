package io.github.dreyes17.courses.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN from ADMIN_EMAIL / ADMIN_PASSWORD, so no credential is ever hard-coded or
 * committed. Without them the app still starts, just with no admin account.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AccountService accounts;
    private final String email;
    private final String password;

    AdminBootstrap(AccountService accounts, @Value("${app.security.admin.email:}") String email,
                   @Value("${app.security.admin.password:}") String password) {
        this.accounts = accounts;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.warn("ADMIN_EMAIL/ADMIN_PASSWORD not set: no admin account was created");
            return;
        }
        if (accounts.ensureAdmin(email, password)) {
            log.info("Created admin account {}", email);
        }
    }
}

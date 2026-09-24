package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Component
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSecurityProperties.BootstrapAdmin settings;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthSecurityProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.settings = properties.bootstrapAdmin();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!settings.configured()) {
            return;
        }
        if (userRepository.existsByRolesContaining(Role.ROLE_ADMIN)) {
            LOGGER.info("An admin account already exists; bootstrap admin skipped. Remove the bootstrap password from config.");
            return;
        }
        String email = settings.email().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            LOGGER.error("Bootstrap admin email already belongs to an existing non-admin account; bootstrap admin skipped.");
            return;
        }
        if (settings.password().length() < MIN_PASSWORD_LENGTH) {
            LOGGER.error("Bootstrap admin password must be at least {} characters; bootstrap admin skipped.", MIN_PASSWORD_LENGTH);
            return;
        }
        User admin = new User(email, passwordEncoder.encode(settings.password()), new HashSet<>(Set.of(Role.ROLE_ADMIN)));
        admin.setEmailVerifiedAt(Instant.now());
        userRepository.save(admin);
        LOGGER.warn("AUDIT bootstrap admin created: {}. Remove the bootstrap password from config.", email);
    }
}

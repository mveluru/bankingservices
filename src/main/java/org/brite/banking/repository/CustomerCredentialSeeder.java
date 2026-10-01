package org.brite.banking.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.entity.CustomerCredentialEntity;
import org.brite.banking.repository.jpa.CustomerCredentialJpaRepository;
import org.brite.banking.repository.jpa.CustomerJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Gives the first {@value #DEMO_LOGINS} customers (lowest ids) a DEMO login, but only if
 * customer_credentials is empty (same run-once rule as the other seeders). The username is
 * {@code customer} + the four-digit customer id and the password {@code 2026} + the same four digits
 * ({@code customer0001} / {@code 20260001}). Fictional demo customers only; never reuse this scheme
 * for real people, and only the BCrypt hash is stored. Other customers (including the portal's test
 * customers) get no login.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerCredentialSeeder {
    static final int DEMO_LOGINS = 10;

    private final CustomerCredentialJpaRepository credentialJpaRepository;
    private final CustomerJpaRepository customerJpaRepository;
    private final PasswordEncoder passwordEncoder;
    /** Forces the demo customers to be seeded first. */
    @SuppressWarnings("unused")
    private final AccountDataSeeder accountDataSeeder;

    @PostConstruct
    @Transactional
    public void seedIfEmpty() {
        if (credentialJpaRepository.count() > 0) {
            log.info("Customer credentials table already has data; skipping demo seed");
            return;
        }
        log.info("Seeding demo customer logins");
        customerJpaRepository.findAll(PageRequest.of(0, DEMO_LOGINS, Sort.by("id"))).forEach(customer -> {
            String digits = String.format("%04d", customer.getId());
            credentialJpaRepository.save(CustomerCredentialEntity.builder()
                    .customerId(customer.getId())
                    .username("customer" + digits)
                    .passwordHash(passwordEncoder.encode("2026" + digits))
                    .passwordChangedAt(LocalDateTime.now())
                    .build());
        });
    }
}

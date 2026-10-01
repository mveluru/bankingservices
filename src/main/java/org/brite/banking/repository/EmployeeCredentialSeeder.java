package org.brite.banking.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.entity.EmployeeCredentialEntity;
import org.brite.banking.entity.EmployeeEntity;
import org.brite.banking.repository.jpa.EmployeeCredentialJpaRepository;
import org.brite.banking.repository.jpa.EmployeeJpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Gives every seeded demo employee a login, but only if bank_employee_credentials is empty (same
 * run-once rule as the other seeders). The username is the email's local part
 * ({@code lucas.meyer}) and the password is the DEMO value {@code 2026} + the four-digit employee
 * sequence ({@code EMP-000010} -> {@code 20260010}). These are fictional employees in a demo
 * database; never reuse this scheme for real people. Only the BCrypt hash is stored.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EmployeeCredentialSeeder {
    private final EmployeeCredentialJpaRepository credentialJpaRepository;
    private final EmployeeJpaRepository employeeJpaRepository;
    private final PasswordEncoder passwordEncoder;
    /** Forces the employees to be seeded first. */
    @SuppressWarnings("unused")
    private final EmployeeDataSeeder employeeDataSeeder;

    @PostConstruct
    @Transactional
    public void seedIfEmpty() {
        if (credentialJpaRepository.count() > 0) {
            log.info("Employee credentials table already has data; skipping demo seed");
            return;
        }
        log.info("Seeding demo employee logins");
        for (EmployeeEntity employee : employeeJpaRepository.findAll()) {
            String sequence = employee.getEmployeeNumber().substring(employee.getEmployeeNumber().length() - 4);
            credentialJpaRepository.save(EmployeeCredentialEntity.builder()
                    .employeeId(employee.getId())
                    .username(employee.getEmail().substring(0, employee.getEmail().indexOf('@')))
                    .passwordHash(passwordEncoder.encode("2026" + sequence))
                    .passwordChangedAt(LocalDateTime.now())
                    .build());
        }
    }
}

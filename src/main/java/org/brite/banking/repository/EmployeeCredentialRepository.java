package org.brite.banking.repository;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.EmployeeCredential;
import org.brite.banking.entity.EmployeeCredentialEntity;
import org.brite.banking.repository.jpa.EmployeeCredentialJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Facade over {@link EmployeeCredentialJpaRepository}; maps entities to {@link EmployeeCredential}. */
@Repository
@RequiredArgsConstructor
public class EmployeeCredentialRepository {
    private final EmployeeCredentialJpaRepository jpaRepository;

    @Transactional(readOnly = true)
    public Optional<EmployeeCredential> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(EmployeeCredentialRepository::toDomain);
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeCredential> findByEmployeeId(Long employeeId) {
        return jpaRepository.findByEmployeeId(employeeId).map(EmployeeCredentialRepository::toDomain);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmployeeId(Long employeeId) {
        return jpaRepository.findByEmployeeId(employeeId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }

    /** Inserts the login, or updates the existing one for the same employee. */
    @Transactional
    public EmployeeCredential save(EmployeeCredential credential) {
        EmployeeCredentialEntity entity = jpaRepository.findByEmployeeId(credential.getEmployeeId())
                .orElseGet(EmployeeCredentialEntity::new);
        entity.setEmployeeId(credential.getEmployeeId());
        entity.setUsername(credential.getUsername());
        entity.setPasswordHash(credential.getPasswordHash());
        entity.setFailedAttempts(credential.getFailedAttempts());
        entity.setLockedUntil(credential.getLockedUntil());
        entity.setLastLoginAt(credential.getLastLoginAt());
        entity.setPasswordChangedAt(credential.getPasswordChangedAt());
        entity.setStatus(credential.getStatus());
        entity.setStatusReason(credential.getStatusReason());
        entity.setStatusChangedAt(credential.getStatusChangedAt());
        return toDomain(jpaRepository.save(entity));
    }

    private static EmployeeCredential toDomain(EmployeeCredentialEntity e) {
        return EmployeeCredential.builder()
                .employeeId(e.getEmployeeId())
                .username(e.getUsername())
                .passwordHash(e.getPasswordHash())
                .failedAttempts(e.getFailedAttempts())
                .lockedUntil(e.getLockedUntil())
                .lastLoginAt(e.getLastLoginAt())
                .passwordChangedAt(e.getPasswordChangedAt())
                .status(e.getStatus())
                .statusReason(e.getStatusReason())
                .statusChangedAt(e.getStatusChangedAt())
                .build();
    }
}

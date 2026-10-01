package org.brite.banking.repository;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.CustomerCredential;
import org.brite.banking.entity.CustomerCredentialEntity;
import org.brite.banking.repository.jpa.CustomerCredentialJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Facade over {@link CustomerCredentialJpaRepository}; maps entities to {@link CustomerCredential}. */
@Repository
@RequiredArgsConstructor
public class CustomerCredentialRepository {
    private final CustomerCredentialJpaRepository jpaRepository;

    @Transactional(readOnly = true)
    public Optional<CustomerCredential> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(CustomerCredentialRepository::toDomain);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerCredential> findByCustomerId(Long customerId) {
        return jpaRepository.findByCustomerId(customerId).map(CustomerCredentialRepository::toDomain);
    }

    @Transactional(readOnly = true)
    public boolean existsByCustomerId(Long customerId) {
        return jpaRepository.findByCustomerId(customerId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    /** Inserts the login, or updates the existing one for the same customer. */
    @Transactional
    public CustomerCredential save(CustomerCredential credential) {
        CustomerCredentialEntity entity = jpaRepository.findByCustomerId(credential.getCustomerId())
                .orElseGet(CustomerCredentialEntity::new);
        entity.setCustomerId(credential.getCustomerId());
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

    private static CustomerCredential toDomain(CustomerCredentialEntity e) {
        return CustomerCredential.builder()
                .customerId(e.getCustomerId())
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

package org.brite.banking.repository.jpa;

import org.brite.banking.entity.CustomerCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerCredentialJpaRepository extends JpaRepository<CustomerCredentialEntity, Long> {
    Optional<CustomerCredentialEntity> findByUsername(String username);

    Optional<CustomerCredentialEntity> findByCustomerId(Long customerId);

    boolean existsByUsername(String username);
}

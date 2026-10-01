package org.brite.banking.repository.jpa;

import org.brite.banking.entity.EmployeeCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeCredentialJpaRepository extends JpaRepository<EmployeeCredentialEntity, Long> {
    Optional<EmployeeCredentialEntity> findByUsername(String username);

    Optional<EmployeeCredentialEntity> findByEmployeeId(Long employeeId);

    boolean existsByUsername(String username);
}

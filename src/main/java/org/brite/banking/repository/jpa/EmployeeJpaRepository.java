package org.brite.banking.repository.jpa;

import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.entity.EmployeeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeJpaRepository extends JpaRepository<EmployeeEntity, Long> {
    Optional<EmployeeEntity> findByEmployeeNumber(String employeeNumber);

    Page<EmployeeEntity> findByRole(EmployeeRole role, Pageable pageable);

    List<EmployeeEntity> findByRoleOrderByLastNameAscFirstNameAsc(EmployeeRole role);
}

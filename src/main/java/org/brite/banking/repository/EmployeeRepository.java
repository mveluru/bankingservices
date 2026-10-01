package org.brite.banking.repository;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.entity.EmployeeEntity;
import org.brite.banking.repository.jpa.EmployeeJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Facade over {@link EmployeeJpaRepository} that maps entities to {@link Employee}; read-only for now. */
@Repository
@RequiredArgsConstructor
public class EmployeeRepository {
    private final EmployeeJpaRepository employeeJpaRepository;

    public Optional<Employee> findByEmployeeNumber(String employeeNumber) {
        return employeeJpaRepository.findByEmployeeNumber(employeeNumber).map(EmployeeRepository::toDomain);
    }

    public List<Employee> findByRole(EmployeeRole role) {
        return employeeJpaRepository.findByRoleOrderByLastNameAscFirstNameAsc(role).stream()
                .map(EmployeeRepository::toDomain).toList();
    }

    /** All employees, or only those with {@code role} when it is non-null. */
    public Page<Employee> search(EmployeeRole role, Pageable pageable) {
        Page<EmployeeEntity> page = role == null
                ? employeeJpaRepository.findAll(pageable)
                : employeeJpaRepository.findByRole(role, pageable);
        return page.map(EmployeeRepository::toDomain);
    }

    static Employee toDomain(EmployeeEntity e) {
        return Employee.builder()
                .id(e.getId())
                .employeeNumber(e.getEmployeeNumber())
                .firstName(e.getFirstName())
                .lastName(e.getLastName())
                .email(e.getEmail())
                .phoneNumber(e.getPhoneNumber())
                .role(e.getRole())
                .jobTitle(e.getJobTitle())
                .status(e.getStatus())
                .hireDate(e.getHireDate())
                .bankLocationId(e.getBankLocationId())
                .region(e.getRegion())
                .supervisorId(e.getSupervisorId())
                .build();
    }
}

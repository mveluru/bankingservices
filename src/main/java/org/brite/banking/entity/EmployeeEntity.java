package org.brite.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;

import java.time.LocalDate;

/**
 * Persistent form of {@link org.brite.banking.domain.Employee}; one row per bank employee.
 * {@code bankLocationId} and {@code supervisorId} are plain ids, not foreign keys, so removing a
 * location or employee never cascades into staff records. Privileges are not stored: they come
 * from {@code role}.
 */
@Entity
@Table(name = "bank_employees", indexes = {
        @Index(name = "idx_bank_employees_employee_number", columnList = "employeeNumber", unique = true),
        @Index(name = "idx_bank_employees_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String employeeNumber;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeRole role;

    @Column(nullable = false, length = 100)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDate hireDate;

    /** Branch the employee works at; null for area managers. */
    private Long bankLocationId;

    /** Area covered; set for area managers. */
    @Column(length = 50)
    private String region;

    /** Employee this one reports to; null at the top of the chain. */
    private Long supervisorId;
}

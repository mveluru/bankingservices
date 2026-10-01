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
import lombok.ToString;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.SecurityQuestion;

import java.time.LocalDateTime;

/**
 * One security answer of one employee or customer, in its own table. Each owner has exactly three slots (1..3), replaced in
 * place when they pick new questions, so there is never a delete. Only a BCrypt hash of the normalised answer is stored.
 * {@code ownerId} is a plain id (employee id or customer id), not a foreign key.
 */
@Entity
@Table(name = "security_answers", indexes = {
        @Index(name = "idx_security_answers_owner_slot", columnList = "ownerType, ownerId, slot", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "answerHash")
public class SecurityAnswerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialOwnerType ownerType;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private int slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityQuestion question;

    /** BCrypt hash (60 chars). */
    @Column(nullable = false, length = 100)
    private String answerHash;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

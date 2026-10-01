package org.brite.banking.repository;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.StoredSecurityAnswer;
import org.brite.banking.entity.SecurityAnswerEntity;
import org.brite.banking.repository.jpa.SecurityAnswerJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Facade over {@link SecurityAnswerJpaRepository}: a user's three answers, replaced in place by slot (never deleted). */
@Repository
@RequiredArgsConstructor
public class SecurityAnswerRepository {
    private final SecurityAnswerJpaRepository jpaRepository;

    @Transactional(readOnly = true)
    public List<StoredSecurityAnswer> findByOwner(CredentialOwnerType type, Long ownerId) {
        return jpaRepository.findByOwnerTypeAndOwnerIdOrderBySlotAsc(type, ownerId).stream()
                .map(e -> new StoredSecurityAnswer(e.getSlot(), e.getQuestion(), e.getAnswerHash()))
                .toList();
    }

    /** Writes each answer into its slot, updating the existing row for that slot or inserting it. */
    @Transactional
    public void saveAll(CredentialOwnerType type, Long ownerId, List<StoredSecurityAnswer> answers) {
        List<SecurityAnswerEntity> existing = jpaRepository.findByOwnerTypeAndOwnerIdOrderBySlotAsc(type, ownerId);
        for (StoredSecurityAnswer answer : answers) {
            SecurityAnswerEntity entity = existing.stream().filter(e -> e.getSlot() == answer.getSlot()).findFirst()
                    .orElseGet(() -> SecurityAnswerEntity.builder().ownerType(type).ownerId(ownerId).slot(answer.getSlot()).build());
            entity.setQuestion(answer.getQuestion());
            entity.setAnswerHash(answer.getAnswerHash());
            entity.setUpdatedAt(LocalDateTime.now());
            jpaRepository.save(entity);
        }
    }
}

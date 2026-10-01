package org.brite.banking.repository.jpa;

import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.entity.SecurityAnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityAnswerJpaRepository extends JpaRepository<SecurityAnswerEntity, Long> {
    List<SecurityAnswerEntity> findByOwnerTypeAndOwnerIdOrderBySlotAsc(CredentialOwnerType ownerType, Long ownerId);
}

package org.brite.banking.repository.jpa;

import org.brite.banking.entity.WithdrawalHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WithdrawalHistoryJpaRepository extends JpaRepository<WithdrawalHistoryEntity, Long> {
}

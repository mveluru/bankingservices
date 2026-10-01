package org.brite.banking.repository.jpa;

import org.brite.banking.entity.AccountTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountTransactionJpaRepository extends JpaRepository<AccountTransactionEntity, Long> {
    List<AccountTransactionEntity> findByAccount_AccountNumber(String accountNumber);
}

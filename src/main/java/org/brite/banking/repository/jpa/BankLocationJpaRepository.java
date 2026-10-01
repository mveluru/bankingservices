package org.brite.banking.repository.jpa;

import org.brite.banking.entity.BankLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface BankLocationJpaRepository extends JpaRepository<BankLocationEntity, Long>, JpaSpecificationExecutor<BankLocationEntity> {
}

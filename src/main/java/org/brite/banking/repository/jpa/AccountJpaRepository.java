package org.brite.banking.repository.jpa;

import org.brite.banking.domain.AccountStatus;
import org.brite.banking.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Raw Spring Data JPA repository for {@link AccountEntity}. Application code talks to
 * {@link org.brite.banking.repository.AccountRepository} instead, which wraps this and maps
 * to/from the {@link org.brite.banking.domain.Account} domain object; this interface exists
 * so that wrapper can get real DB-backed CRUD, dynamic filtering (via
 * {@link JpaSpecificationExecutor}), and native pagination/sorting.
 */
@Repository
public interface AccountJpaRepository extends JpaRepository<AccountEntity, Long>, JpaSpecificationExecutor<AccountEntity> {
    Optional<AccountEntity> findByAccountNumber(String accountNumber);

    /** The status of every account the customer owns (checking and savings are separate rows). */
    @Query("select a.accountStatus from AccountEntity a where a.customer.id = :customerId")
    List<AccountStatus> findStatusesByCustomerId(@Param("customerId") Long customerId);

    /** Suspended accounts whose end time has passed (indefinite suspensions have a null end and never match). */
    List<AccountEntity> findBySuspendedTrueAndSuspendedEndLessThanEqual(LocalDateTime now);
}

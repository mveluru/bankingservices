package org.brite.banking.repository.jpa;

import org.brite.banking.entity.EmployeeRateLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface EmployeeRateLimitJpaRepository extends JpaRepository<EmployeeRateLimitEntity, Long> {
    Optional<EmployeeRateLimitEntity> findByEmployeeNumber(String employeeNumber);

    /** Zeroes the counters of a row that still holds an earlier day's usage. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmployeeRateLimitEntity e set e.usageDate = :today, e.requestCount = 0, e.loginCount = 0 "
            + "where e.employeeNumber = :employeeNumber and e.usageDate <> :today")
    int startNewDay(@Param("employeeNumber") String employeeNumber, @Param("today") LocalDate today);

    /** Counts one request only while today's count is below {@code limit}, in one statement so concurrent requests can't overshoot. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmployeeRateLimitEntity e set e.requestCount = e.requestCount + 1 "
            + "where e.employeeNumber = :employeeNumber and e.usageDate = :today and e.requestCount < :limit")
    int consumeRequest(@Param("employeeNumber") String employeeNumber, @Param("today") LocalDate today, @Param("limit") int limit);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmployeeRateLimitEntity e set e.maxRequestsPerDay = :max where e.employeeNumber = :employeeNumber")
    int setMaxRequestsPerDay(@Param("employeeNumber") String employeeNumber, @Param("max") Integer max);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmployeeRateLimitEntity e set e.loginCount = e.loginCount + 1 "
            + "where e.employeeNumber = :employeeNumber and e.usageDate = :today")
    int addLogin(@Param("employeeNumber") String employeeNumber, @Param("today") LocalDate today);
}

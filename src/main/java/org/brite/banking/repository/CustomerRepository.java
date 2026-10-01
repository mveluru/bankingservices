package org.brite.banking.repository;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.repository.jpa.CustomerJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Read-only facade over {@link CustomerJpaRepository} for the login flow; exposes only id and name. */
@Repository
@RequiredArgsConstructor
public class CustomerRepository {
    private final CustomerJpaRepository customerJpaRepository;

    @Transactional(readOnly = true)
    public Optional<AuthenticatedCustomer> findIdentityById(Long id) {
        return customerJpaRepository.findById(id).map(c -> AuthenticatedCustomer.builder()
                .customerId(c.getId()).firstName(c.getFirstName()).lastName(c.getLastName()).build());
    }
}

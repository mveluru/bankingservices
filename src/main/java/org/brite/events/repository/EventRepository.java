package org.brite.events.repository;


import org.brite.events.dto.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, String> {
    // Standard CRUD methods are automatically included
}
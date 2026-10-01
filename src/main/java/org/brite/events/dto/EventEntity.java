package org.brite.events.dto;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "events")
public class EventEntity {
    @Id
    private String eventId;
    private String version;
    private ZonedDateTime timestamp;
    private String userId;
    private String email;

    // Getters, Setters, Constructors
    public EventEntity() {}
    public EventEntity(String eventId, String version, ZonedDateTime timestamp, String userId, String email) {
        this.eventId = eventId;
        this.version = version;
        this.timestamp = timestamp;
        this.userId = userId;
        this.email = email;
    }
    // ... Boilerplate getters/setters
}

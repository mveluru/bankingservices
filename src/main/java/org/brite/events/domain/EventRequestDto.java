package org.brite.events.domain;



import java.time.ZonedDateTime;

public record EventRequestDto(String version, String eventId, ZonedDateTime timestamp, PayloadDto payload) {}


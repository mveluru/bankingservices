package org.brite.events.controller;




import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.brite.events.domain.EventRequestDto;
import org.brite.events.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final ObjectMapper objectMapper;
    private final JsonSchema schemaV1;

    public EventController(EventService eventService, ObjectMapper objectMapper) {
        this.eventService = eventService;
        this.objectMapper = objectMapper;

        // Load the v1 schema from resources during initialization
        InputStream schemaStream = getClass().getResourceAsStream("/schemas/event-v1.json");
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        this.schemaV1 = factory.getSchema(schemaStream);
    }

    @PostMapping
    public ResponseEntity<?> receiveEvent(@RequestBody String rawJson) {
        try {
            // 1. Parse raw string to JsonNode
            JsonNode jsonNode = objectMapper.readTree(rawJson);

            // 2. Route versioning dynamically based on payload content
            String version = jsonNode.has("version") ? jsonNode.get("version").asText() : "unknown";

            if ("v1".equals(version)) {
                // 3. Perform JSON Schema Validation
                Set<ValidationMessage> errors = schemaV1.validate(jsonNode);
                if (!errors.isEmpty()) {
                    String errorMsg = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining(", "));
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Schema Validation Failed: " + errorMsg);
                }

                // 4. Bind to DTO and pass to service layer once proven valid
                EventRequestDto dto = objectMapper.treeToValue(jsonNode, EventRequestDto.class);
                eventService.processAndSaveEvent(dto);
                return ResponseEntity.ok("Event v1 processed and saved successfully.");
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unsupported DTO version: " + version);

        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Malformed JSON: " + e.getOriginalMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }
}


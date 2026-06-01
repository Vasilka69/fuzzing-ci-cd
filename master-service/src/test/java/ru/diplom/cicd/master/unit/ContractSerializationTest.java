package ru.diplom.cicd.master.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.master.service.messaging.contract.CancelCommand;
import ru.diplom.cicd.master.service.messaging.contract.JobMessage;

class ContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesCamelCaseFields() throws Exception {
        JobMessage message = new JobMessage(
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "build",
                "build/maven",
                1,
                3,
                1800,
                Map.of("cpu", "2"),
                Map.of("cleanup", "always"),
                Map.of(),
                Map.of(),
                Map.of("refs", new String[0]),
                OffsetDateTime.now()
        );
        String json = objectMapper.writeValueAsString(message);
        assertTrue(json.contains("\"schemaVersion\":1"));
        assertTrue(json.contains("\"jobExecutionId\""));
        assertTrue(json.contains("\"templatePath\""));
    }

    @Test
    void cancelCommandContainsRequiredFields() throws Exception {
        CancelCommand command = new CancelCommand(
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user_requested",
                "user-1",
                30,
                OffsetDateTime.now()
        );
        String json = objectMapper.writeValueAsString(command);
        assertTrue(json.contains("\"gracePeriodSeconds\":30"));
        assertTrue(json.contains("\"requestedAt\""));
    }
}

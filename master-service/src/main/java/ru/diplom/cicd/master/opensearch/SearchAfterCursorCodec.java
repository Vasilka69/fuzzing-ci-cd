package ru.diplom.cicd.master.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SearchAfterCursorCodec {

    private final ObjectMapper objectMapper;

    public String encode(List<Object> searchAfter) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(searchAfter);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot encode cursor", ex);
        }
    }

    public List<Object> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return List.of();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor.getBytes(StandardCharsets.UTF_8));
            return objectMapper.readValue(bytes, objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid cursor", ex);
        }
    }
}

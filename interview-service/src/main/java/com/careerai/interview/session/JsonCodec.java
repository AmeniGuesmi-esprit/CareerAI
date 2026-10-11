package com.careerai.interview.session;

import com.careerai.interview.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JsonCodec {

    private final ObjectMapper mapper;

    public String write(Object value) {
        try {
            return value == null ? null : mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation impossible", e);
        }
    }

    public List<String> strings(String json) {
        return read(json, new TypeReference<List<String>>() { }, List.of());
    }

    public Map<String, String> map(String json) {
        return read(json, new TypeReference<Map<String, String>>() { }, null);
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORED_JSON_INVALID", "Donnée stockée illisible.");
        }
    }
}

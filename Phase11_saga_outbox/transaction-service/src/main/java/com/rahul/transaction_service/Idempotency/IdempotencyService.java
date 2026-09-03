package com.rahul.transaction_service.Idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    // Looks up an existing record for this key. Empty = never seen before, proceed normally.
    public Optional<IdempotencyKey> checkExisting(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
    }

    // Converts a stored responseBody (JSON string) back into the actual response type.
    @SneakyThrows
    public <T> T deserializeResponse(String responseBody, Class<T> type) {
        return objectMapper.readValue(responseBody, type);
    }
}
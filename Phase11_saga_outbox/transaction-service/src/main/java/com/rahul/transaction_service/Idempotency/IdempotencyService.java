package com.rahul.transaction_service.Idempotency;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.Optional;


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


    // NEW — saves the key + response after a successful transfer
    @SneakyThrows
    public void save(String idempotencyKey, Object request, int responseStatus, Object response) {
        IdempotencyKey record = IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(hashRequest(request))
                .responseStatus(responseStatus)
                .responseBody(objectMapper.writeValueAsString(response))
                .build();

        idempotencyKeyRepository.save(record);
    }

    // NEW — turns the request object into a short fixed-length hash, used to
    // detect if the same key gets reused for a genuinely different request
    @SneakyThrows
    public String hashRequest(Object request) {
        String json = objectMapper.writeValueAsString(request);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(json.getBytes());

        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
package com.rahul.transaction_service.Idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    // we are trying to save idempotencyKey as soon as it arrives as processing
    //if there is already key present then it will throw error due to key is unique
    //The reason this is safe under concurrency (unlike a plain "check if exists, then save" approach) is:
    // the database enforces uniqueness atomically, at the exact moment of insertion.
    // Even if two requests both try to insert at the literal same microsecond,
    // the database guarantees only one insert can physically succeed
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyKey registerProcessing(String idempotencyKey, Object request) {
        IdempotencyKey record = IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(hashRequest(request))
                .status(IdempotencyStatus.PROCESSING)
                .build();

        return idempotencyKeyRepository.save(record); // throws DataIntegrityViolationException on race
    }

    public Optional<IdempotencyKey> checkExisting(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
    }

    // NEW — fills in the result once the real transfer finishes
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String idempotencyKey, int responseStatus, Object response) {
        IdempotencyKey record = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(); // should always exist — we reserved it ourselves moments ago

        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setResponseStatus(responseStatus);
        record.setResponseBody(serialize(response));
        record.setCompletedAt(LocalDateTime.now());

        idempotencyKeyRepository.save(record);
    }

    @SneakyThrows
    public <T> T deserializeResponse(String responseBody, Class<T> type) {
        return objectMapper.readValue(responseBody, type);
    }

    public boolean matchesOriginalRequest(IdempotencyKey record, Object incomingRequest) {
        return record.getRequestHash().equals(hashRequest(incomingRequest));
    }

    @SneakyThrows
    private String serialize(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }

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
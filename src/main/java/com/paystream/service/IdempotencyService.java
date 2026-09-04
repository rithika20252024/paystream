package com.paystream.service;

import com.paystream.model.IdempotencyRecord;
import com.paystream.repository.IdempotencyRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;

    public IdempotencyService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    public Optional<IdempotencyRecord> getRecord(String idempotencyKey) {
        return idempotencyRepository.findByIdempotencyKey(idempotencyKey);
    }

    public void saveRecord(String idempotencyKey, String requestHash, String responseBody, int status) {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .responseBody(responseBody)
                .responseStatus(status)
                .build();
        idempotencyRepository.save(record);
    }
}

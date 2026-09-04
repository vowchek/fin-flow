package com.ledger.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

public class ResourceNotFoundException extends ResponseStatusException {

    public ResourceNotFoundException(String resource, UUID id) {
        super(HttpStatus.NOT_FOUND, resource + " not found: " + id);
    }

    public ResourceNotFoundException(String resource, String key) {
        super(HttpStatus.NOT_FOUND, resource + " not found: " + key);
    }
}

package com.ledger.infrastructure.security;

import com.ledger.domain.UserRole;

import java.util.UUID;

public record UserPrincipal(UUID id, String email, UserRole role) {
}

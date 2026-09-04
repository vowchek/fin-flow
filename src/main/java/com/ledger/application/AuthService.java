package com.ledger.application;



import com.ledger.api.dto.AuthResponse;

import com.ledger.api.dto.LoginRequest;

import com.ledger.api.dto.RegisterRequest;

import com.ledger.api.dto.UserResponse;

import com.ledger.domain.AppUser;

import com.ledger.domain.UserRole;

import com.ledger.infrastructure.persistence.UserRepository;

import com.ledger.infrastructure.security.JwtService;

import com.ledger.infrastructure.security.UserPrincipal;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.security.authentication.BadCredentialsException;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.util.Arrays;

import java.util.Locale;

import java.util.Set;

import java.util.UUID;

import java.util.stream.Collectors;



@Service

public class AuthService {



    private final UserRepository users;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final CurrentUserService currentUser;

    private final Set<String> adminEmails;



    public AuthService(

            UserRepository users,

            PasswordEncoder passwordEncoder,

            JwtService jwtService,

            CurrentUserService currentUser,

            @Value("${ledger.security.admin-emails:}") String adminEmails

    ) {

        this.users = users;

        this.passwordEncoder = passwordEncoder;

        this.jwtService = jwtService;

        this.currentUser = currentUser;

        this.adminEmails = Arrays.stream(adminEmails.split(","))

                .map(String::trim)

                .filter(s -> !s.isEmpty())

                .map(s -> s.toLowerCase(Locale.ROOT))

                .collect(Collectors.toSet());

    }



    @Transactional

    public AuthResponse register(RegisterRequest request) {

        String email = normalizeEmail(request.email());

        if (users.existsByEmailIgnoreCase(email)) {

            throw new ConflictException("Email already registered");

        }



        UserRole role = adminEmails.contains(email) ? UserRole.ADMIN : UserRole.USER;

        AppUser user = new AppUser(

                UUID.randomUUID(),

                email,

                passwordEncoder.encode(request.password()),

                trimToNull(request.displayName()),

                role

        );

        try {

            users.save(user);

        } catch (DataIntegrityViolationException ex) {

            throw new ConflictException("Email already registered");

        }

        return issueToken(user);

    }



    @Transactional

    public AuthResponse login(LoginRequest request) {

        String email = normalizeEmail(request.email());

        AppUser user = users.findByEmailIgnoreCase(email)

                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {

            throw new BadCredentialsException("Invalid email or password");

        }

        if (adminEmails.contains(email) && user.getRole() != UserRole.ADMIN) {

            user.setRole(UserRole.ADMIN);

        }

        return issueToken(user);

    }



    @Transactional(readOnly = true)

    public UserResponse me() {

        UserPrincipal principal = currentUser.requirePrincipal();

        AppUser user = users.findById(principal.id())

                .orElseThrow(() -> new ResourceNotFoundException("User", principal.id()));

        return toResponse(user);

    }



    private AuthResponse issueToken(AppUser user) {

        String token = jwtService.createToken(user.getId(), user.getEmail(), user.getRole());

        return new AuthResponse(token, "Bearer", jwtService.expirationSeconds(), toResponse(user));

    }



    private static UserResponse toResponse(AppUser user) {

        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());

    }



    private static String normalizeEmail(String email) {

        return email.trim().toLowerCase(Locale.ROOT);

    }



    private static String trimToNull(String value) {

        if (value == null) {

            return null;

        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;

    }

}



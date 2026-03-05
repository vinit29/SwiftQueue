package com.swiftqueue.queue.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftqueue.queue.dto.AuthResponse;
import com.swiftqueue.queue.dto.LoginRequest;
import com.swiftqueue.queue.dto.SignUpRequest;
import com.swiftqueue.queue.model.Owner;
import com.swiftqueue.queue.repository.OwnerRepository;
import com.swiftqueue.queue.security.JwtTokenProvider;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthController(OwnerRepository ownerRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.ownerRepository = ownerRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerOwner(@RequestBody SignUpRequest signUpRequest) {
        if (ownerRepository.existsByEmail(signUpRequest.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already taken!"));
        }

        Owner owner = new Owner();
        owner.setEmail(signUpRequest.email());
        owner.setPassword(passwordEncoder.encode(signUpRequest.password()));
        owner.setBusinessName(signUpRequest.businessName());
        owner.setBusinessType(signUpRequest.businessType());

        Owner savedOwner = ownerRepository.save(owner);
        
        // TODO: Initialize counter for this owner
        
        String token = tokenProvider.generateToken(savedOwner.getId());

        AuthResponse response = new AuthResponse(token, savedOwner.getId(), savedOwner.getBusinessName(), savedOwner.getBusinessType());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateOwner(@RequestBody LoginRequest loginRequest) {
        // Here you would use AuthenticationManager to validate credentials
        // Then, use a JwtTokenProvider to generate a token
        // Finally, find the owner details and return an AuthResponse
        
        // Simplified logic:
        Owner owner = ownerRepository.findByEmail(loginRequest.email()).orElse(null);
        if (owner == null || !passwordEncoder.matches(loginRequest.password(), owner.getPassword())) {
             return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        String token = tokenProvider.generateToken(owner.getId());
        
        AuthResponse response = new AuthResponse(token, owner.getId(), owner.getBusinessName(), owner.getBusinessType());
        return ResponseEntity.ok(response);
    }
}
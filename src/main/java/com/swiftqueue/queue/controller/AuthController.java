package com.swiftqueue.queue.controller;

import java.util.Arrays;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftqueue.queue.dto.AuthResponse;
import com.swiftqueue.queue.dto.LoginRequest;
import com.swiftqueue.queue.dto.SignUpRequest;
import com.swiftqueue.queue.dto.UserSignUpRequest;
import com.swiftqueue.queue.model.Counter;
import com.swiftqueue.queue.model.Owner;
import com.swiftqueue.queue.model.User;
import com.swiftqueue.queue.repository.CounterRepository;
import com.swiftqueue.queue.repository.OwnerRepository;
import com.swiftqueue.queue.repository.UserRepository;
import com.swiftqueue.queue.security.JwtTokenProvider;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final OwnerRepository ownerRepository;
    private final UserRepository userRepository;
    private final CounterRepository counterRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthController(OwnerRepository ownerRepository, UserRepository userRepository, CounterRepository counterRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.ownerRepository = ownerRepository;
        this.userRepository = userRepository;
        this.counterRepository = counterRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/signup")
    @Transactional
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
        
        // Initialize counter for this owner with default sequence 100 and average time 30 mins
        counterRepository.save(new Counter(savedOwner.getId(), 100, 30));
        
        String token = tokenProvider.generateToken(savedOwner.getId(), Arrays.asList("ROLE_OWNER"));

        AuthResponse response = new AuthResponse(token, savedOwner.getId(), savedOwner.getBusinessName(), savedOwner.getBusinessType());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> authenticateOwner(@RequestBody LoginRequest loginRequest) {
        Owner owner = ownerRepository.findByEmail(loginRequest.email()).orElse(null);
        if (owner == null || !passwordEncoder.matches(loginRequest.password(), owner.getPassword())) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid email or password"));
        }
        
        String token = tokenProvider.generateToken(owner.getId(), Arrays.asList("ROLE_OWNER"));
        
        AuthResponse response = new AuthResponse(token, owner.getId(), owner.getBusinessName(), owner.getBusinessType());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/user/signup")
    @Transactional
    public ResponseEntity<?> registerUser(@RequestBody UserSignUpRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already taken!"));
        }

        User user = new User();
        user.setEmail(signUpRequest.email());
        user.setPassword(passwordEncoder.encode(signUpRequest.password()));
        user.setName(signUpRequest.name());
        user.setMobile(signUpRequest.mobile());

        User savedUser = userRepository.save(user);
        
        String token = tokenProvider.generateToken(savedUser.getId(), Arrays.asList("ROLE_USER"));
        
        return ResponseEntity.ok(Map.of("token", token, "id", savedUser.getId(), "name", savedUser.getName()));
    }

    @PostMapping("/user/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.email()).orElse(null);
        
        if (user == null || !passwordEncoder.matches(loginRequest.password(), user.getPassword())) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid email or password"));
        }
        
        String token = tokenProvider.generateToken(user.getId(), Arrays.asList("ROLE_USER"));
        
        return ResponseEntity.ok(Map.of("token", token, "id", user.getId(), "name", user.getName()));
    }
}
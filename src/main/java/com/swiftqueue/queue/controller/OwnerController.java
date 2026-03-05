package com.swiftqueue.queue.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.swiftqueue.queue.model.Owner;
import com.swiftqueue.queue.repository.OwnerRepository;

// TODO: You will need to secure these endpoints with Spring Security
@RestController
@RequestMapping("/api/owner")
public class OwnerController {

    private final OwnerRepository ownerRepository;

    public OwnerController(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

    @GetMapping("/settings")
    public ResponseEntity<Owner> getOwnerSettings(Principal principal) {
        Owner owner = ownerRepository.findById(Long.valueOf(principal.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));

        // Using a DTO is better, but for now, nullify the password to avoid leaking it.
        owner.setPassword(null);

        return ResponseEntity.ok(owner);
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateOwnerSettings(@RequestBody Owner settingsUpdateRequest, Principal principal) {
        Owner owner = ownerRepository.findById(Long.valueOf(principal.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));

        owner.setBusinessType(settingsUpdateRequest.getBusinessType());
        owner.setPaymentModel(settingsUpdateRequest.getPaymentModel());
        ownerRepository.save(owner);

        return ResponseEntity.ok().build();
    }
}
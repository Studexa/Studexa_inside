package com.studexa.game;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.CurrentUser;
import com.studexa.game.dto.PetDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/pet")
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping
    public PetDto pet(@AuthenticationPrincipal Jwt jwt) {
        return petService.getPet(CurrentUser.id(jwt));
    }

    /**
     * Update the pet's mood. Derived server-side from the student's study
     * activity today (reviews in their timezone).
     */
    @PostMapping("/mood")
    public void updateMood(@AuthenticationPrincipal Jwt jwt) {
        petService.updateMood(CurrentUser.id(jwt));
    }

    /**
     * Update the pet's stage. Derived server-side from the student's level.
     */
    @PostMapping("/stage")
    public void updateStage(@AuthenticationPrincipal Jwt jwt) {
        petService.updateStage(CurrentUser.id(jwt));
    }

    /**
     * Rename the pet.
     */
    @PutMapping("/name")
    public PetDto renamePet(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RenamePetRequest request) {
        petService.renamePet(CurrentUser.id(jwt), request.name());
        return petService.getPet(CurrentUser.id(jwt));
    }

    /**
     * Equip or unequip an accessory. Send accessoryType to equip, or empty string to unequip.
     */
    @PostMapping("/equip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void equipAccessory(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EquipAccessoryRequest request) {
        petService.equipAccessory(CurrentUser.id(jwt), request.accessoryType());
    }

    public record EquipAccessoryRequest(String accessoryType) {
    }

    public record RenamePetRequest(
            @Size(min = 1, max = 50, message = "Pet name must be 1 to 50 characters.")
            String name) {
    }
}

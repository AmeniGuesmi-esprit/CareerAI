package com.careerai.user.controller;

import com.careerai.user.dto.*;
import com.careerai.user.model.Role;
import com.careerai.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── Utilisateur connecté (USER ou ADMIN) ──
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(Authentication auth) {
        return ResponseEntity.ok(userService.getMe(auth.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(Authentication auth,
                                                              @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMe(auth.getName(), request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMe(Authentication auth) {
        return ResponseEntity.ok(userService.deleteMe(auth.getName()));
    }

    // ── Administration (ADMIN uniquement) ──
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAll() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> create(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.ok(userService.createByAdmin(request));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(@PathVariable Long id, @RequestParam Role role) {
        return ResponseEntity.ok(userService.changeRole(id, role));
    }

    @PutMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> setEnabled(@PathVariable Long id, @RequestParam boolean value) {
        return ResponseEntity.ok(userService.setEnabled(id, value));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        return ResponseEntity.ok(userService.deleteById(id));
    }
}

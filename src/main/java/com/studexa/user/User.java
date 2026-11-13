package com.studexa.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Login identity. A user has either a password (email sign-up) or a Google id.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private User(String email, String passwordHash, String googleId, UserRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.googleId = googleId;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public static User withPassword(String email, String passwordHash) {
        return new User(email, passwordHash, null, UserRole.USER);
    }

    public static User withGoogle(String email, String googleId) {
        return new User(email, null, googleId, UserRole.USER);
    }
}

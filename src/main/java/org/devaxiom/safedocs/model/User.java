package org.devaxiom.safedocs.model;

import jakarta.persistence.*;
import lombok.*;
import org.devaxiom.safedocs.enums.AuthProviderType;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@SQLDelete(sql = "UPDATE app_user SET deleted = true, is_active = false, version = version + 1 WHERE id = ? AND version = ?")
@DynamicUpdate
@Table(
        name = "app_user",
        uniqueConstraints = @UniqueConstraint(columnNames = "email")
)
public class User extends AbstractAuditable<Long> {

    @Column(name = "public_id", nullable = false, updatable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider_type", length = 20)
    private AuthProviderType authProviderType;

    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "deleted", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Long tokenVersion = 0L;

    @Version
    @Column(name = "version")
    private Long version;

    public Long getUserId() {
        return getId();
    }

    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    @PrePersist
    void initPublicId() {
        if (publicId == null) publicId = UUID.randomUUID();
        if (firstName != null) firstName = firstName.trim();
        if (lastName != null) lastName = lastName.trim();
        if (email != null) email = email.trim().toLowerCase();
    }

    @PreUpdate
    void onUpdate() {
        if (firstName != null) firstName = firstName.trim();
        if (lastName != null) lastName = lastName.trim();
        if (email != null) email = email.trim().toLowerCase();
    }

    @Transient
    private String emailBeforeUpdate;

    @PostLoad
    private void captureEmailBeforeUpdate() {
        this.emailBeforeUpdate = this.email;
    }
}

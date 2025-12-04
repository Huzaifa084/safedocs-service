package org.devaxiom.safedocs.model;

import jakarta.persistence.*;
import lombok.*;
import org.devaxiom.safedocs.enums.FamilyInviteStatus;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "family_invite",
        indexes = @Index(name = "idx_family_invite_public_id", columnList = "public_id", unique = true))
@DynamicUpdate
public class FamilyInvite extends AbstractAuditable<Long> {

    @Column(name = "public_id", nullable = false, updatable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id")
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by")
    private User invitedBy;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FamilyInviteStatus status;

    @PrePersist
    void initDefaults() {
        if (publicId == null) publicId = UUID.randomUUID();
        if (status == null) status = FamilyInviteStatus.PENDING;
        if (email != null) email = email.trim().toLowerCase();
    }

    @PreUpdate
    void onUpdate() {
        if (email != null) email = email.trim().toLowerCase();
    }
}

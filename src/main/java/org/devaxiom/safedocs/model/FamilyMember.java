package org.devaxiom.safedocs.model;

import jakarta.persistence.*;
import lombok.*;
import org.devaxiom.safedocs.enums.FamilyRole;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "family_member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"family_id", "user_id"}))
@DynamicUpdate
public class FamilyMember extends AbstractAuditable<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id")
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private FamilyRole role;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @PrePersist
    void initDefaults() {
        if (role == null) role = FamilyRole.MEMBER;
        if (active == null) active = true;
    }
}

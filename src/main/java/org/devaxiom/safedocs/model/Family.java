package org.devaxiom.safedocs.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "family")
@DynamicUpdate
public class Family extends AbstractAuditable<Long> {

    @Column(name = "public_id", nullable = false, updatable = false, unique = true)
    private UUID publicId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @PrePersist
    void initPublicId() {
        if (publicId == null) publicId = UUID.randomUUID();
        if (name != null) name = name.trim();
    }
}

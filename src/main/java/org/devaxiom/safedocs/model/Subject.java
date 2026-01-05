package org.devaxiom.safedocs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.devaxiom.safedocs.enums.SubjectScope;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "subject",
        indexes = {
                @Index(name = "idx_subject_owner_updated", columnList = "owner_id, updated_at"),
                @Index(name = "idx_subject_family_updated", columnList = "family_id, updated_at")
        }
)
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
public class Subject {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "semester_label", length = 80)
    private String semesterLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private SubjectScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_document_activity_at")
    private LocalDateTime lastDocumentActivityAt;

    @PrePersist
    void initDefaults() {
        if (id == null) id = UUID.randomUUID();
        if (name != null) name = name.trim();
        if (semesterLabel != null) semesterLabel = semesterLabel.trim();
    }

    @PreUpdate
    void onUpdate() {
        if (name != null) name = name.trim();
        if (semesterLabel != null) semesterLabel = semesterLabel.trim();
    }
}

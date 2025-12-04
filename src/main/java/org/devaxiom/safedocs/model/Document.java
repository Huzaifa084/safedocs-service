package org.devaxiom.safedocs.model;

import jakarta.persistence.*;
import lombok.*;
import org.devaxiom.safedocs.enums.DocumentStatus;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "document",
        indexes = {
                @Index(name = "idx_document_public_id", columnList = "public_id", unique = true),
                @Index(name = "idx_document_owner_visibility", columnList = "owner_id, visibility"),
                @Index(name = "idx_document_family", columnList = "family_id")
        })
@DynamicUpdate
public class Document extends AbstractAuditable<Long> {

    @Column(name = "public_id", nullable = false, updatable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private DocumentVisibility visibility;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(name = "storage_filename", length = 200)
    private String storageFilename;

    @Column(name = "storage_mime_type", length = 100)
    private String storageMimeType;

    @Column(name = "storage_size_bytes")
    private Long storageSizeBytes;

    @PrePersist
    void initDefaults() {
        if (publicId == null) publicId = UUID.randomUUID();
        if (status == null) status = DocumentStatus.ACTIVE;
        if (title != null) title = title.trim();
        if (category != null) category = category.trim();
    }

    @PreUpdate
    void onUpdate() {
        if (title != null) title = title.trim();
        if (category != null) category = category.trim();
    }
}

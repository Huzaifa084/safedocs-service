package org.devaxiom.safedocs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.devaxiom.safedocs.enums.DocumentAccessLevel;
import org.devaxiom.safedocs.enums.DocumentReferenceType;
import org.devaxiom.safedocs.enums.DocumentStatus;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.devaxiom.safedocs.enums.StorageProvider;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "document",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_document_owner_drive", columnNames = {"owner_id", "drive_file_id"})
        },
        indexes = {
                @Index(name = "idx_document_public_id", columnList = "public_id", unique = true),
                @Index(name = "idx_document_owner_drive_file", columnList = "owner_id, drive_file_id", unique = true),
                @Index(name = "idx_document_family_visibility", columnList = "family_id, visibility"),
                @Index(name = "idx_document_owner_status", columnList = "owner_id, status"),
                @Index(name = "idx_document_visibility_status", columnList = "visibility, status")
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

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "drive_file_id", nullable = false, length = 200)
    private String driveFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 20)
    private StorageProvider storageProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 20)
    private DocumentReferenceType referenceType;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "drive_created_at")
    private Instant driveCreatedAt;

    @Column(name = "drive_web_view_link", length = 500)
    private String driveWebViewLink;

    @Column(name = "drive_md5", length = 100)
    private String driveMd5;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", length = 20)
    private DocumentAccessLevel accessLevel;

    @PrePersist
    void initDefaults() {
        if (publicId == null) publicId = UUID.randomUUID();
        if (status == null) status = DocumentStatus.ACTIVE;
        if (storageProvider == null) storageProvider = StorageProvider.DRIVE;
        if (referenceType == null) referenceType = DocumentReferenceType.FILE;
        if (title != null) title = title.trim();
        if (fileName != null) fileName = fileName.trim();
        if (category != null) category = category.trim();
        if (driveFileId != null) driveFileId = driveFileId.trim();
        if (mimeType != null) mimeType = mimeType.trim();
        if (driveWebViewLink != null) driveWebViewLink = driveWebViewLink.trim();
        if (driveMd5 != null) driveMd5 = driveMd5.trim();
    }

    @PreUpdate
    void onUpdate() {
        if (title != null) title = title.trim();
        if (fileName != null) fileName = fileName.trim();
        if (category != null) category = category.trim();
        if (driveFileId != null) driveFileId = driveFileId.trim();
        if (mimeType != null) mimeType = mimeType.trim();
        if (driveWebViewLink != null) driveWebViewLink = driveWebViewLink.trim();
        if (driveMd5 != null) driveMd5 = driveMd5.trim();
    }
}

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
import org.devaxiom.safedocs.enums.PermissionJobAction;
import org.devaxiom.safedocs.enums.PermissionJobStatus;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "permission_job",
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "document_public_id", "owner_user_id", "target_user_email", "action"
        }),
        indexes = {
                @Index(name = "idx_permission_job_owner_status", columnList = "owner_user_id, status"),
                @Index(name = "idx_permission_job_family", columnList = "family_id")
        }
)
@DynamicUpdate
public class PermissionJob extends AbstractAuditable<Long> {

    @Column(name = "job_id", nullable = false, updatable = false, unique = true)
    private UUID jobId;

    @Column(name = "document_public_id", nullable = false)
    private UUID documentPublicId;

    @Column(name = "drive_file_id", nullable = false, length = 200)
    private String driveFileId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @Column(name = "target_user_email", nullable = false, length = 150)
    private String targetUserEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private PermissionJobAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PermissionJobStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @PrePersist
    void initDefaults() {
        if (jobId == null) jobId = UUID.randomUUID();
        if (status == null) status = PermissionJobStatus.PENDING;
        if (targetUserEmail != null) targetUserEmail = targetUserEmail.trim().toLowerCase();
        if (driveFileId != null) driveFileId = driveFileId.trim();
        if (attempts < 0) attempts = 0;
    }

    @PreUpdate
    void onUpdate() {
        if (targetUserEmail != null) targetUserEmail = targetUserEmail.trim().toLowerCase();
        if (driveFileId != null) driveFileId = driveFileId.trim();
    }
}

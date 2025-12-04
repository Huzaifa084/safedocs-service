package org.devaxiom.safedocs.model;

import jakarta.persistence.*;
import lombok.*;
import org.devaxiom.safedocs.enums.DocumentShareStatus;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "document_share",
        uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "recipient_email"}))
@DynamicUpdate
public class DocumentShare extends AbstractAuditable<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "recipient_email", nullable = false, length = 150)
    private String recipientEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id")
    private User recipientUser;

    @Column(name = "can_edit", nullable = false)
    private Boolean canEdit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentShareStatus status;

    @PrePersist
    void initDefaults() {
        if (canEdit == null) canEdit = false;
        if (status == null) status = DocumentShareStatus.ACTIVE;
        if (recipientEmail != null) recipientEmail = recipientEmail.trim().toLowerCase();
    }

    @PreUpdate
    void onUpdate() {
        if (recipientEmail != null) recipientEmail = recipientEmail.trim().toLowerCase();
    }
}

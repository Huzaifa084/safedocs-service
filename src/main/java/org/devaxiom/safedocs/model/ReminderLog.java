package org.devaxiom.safedocs.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reminder_log",
        indexes = @Index(name = "idx_reminder_document", columnList = "document_id"))
@DynamicUpdate
public class ReminderLog extends AbstractAuditable<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "recipient_email", nullable = false, length = 150)
    private String recipientEmail;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "reminder_type", length = 50)
    private String reminderType;

    @Column(name = "expiry_snapshot")
    private LocalDate expirySnapshot;

    @PrePersist
    void initDefaults() {
        if (sentAt == null) sentAt = LocalDateTime.now();
        if (recipientEmail != null) recipientEmail = recipientEmail.trim().toLowerCase();
    }
}

package org.devaxiom.safedocs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.devaxiom.safedocs.enums.DocumentActivityAction;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "document_activity",
        indexes = {
                @Index(name = "idx_doc_activity_document_created", columnList = "document_id, created_date"),
                @Index(name = "idx_doc_activity_actor_created", columnList = "actor_user_id, created_date"),
                @Index(name = "idx_doc_activity_action_created", columnList = "action, created_date")
        }
)
@DynamicUpdate
public class DocumentActivity extends AbstractAuditable<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private DocumentActivityAction action;
}

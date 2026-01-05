package org.devaxiom.safedocs.service;

import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.enums.DocumentActivityAction;
import org.devaxiom.safedocs.model.Document;
import org.devaxiom.safedocs.model.DocumentActivity;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.repository.DocumentActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentActivityService {

    private final DocumentActivityRepository documentActivityRepository;

    @Transactional
    public void record(Document document, User actorUser, DocumentActivityAction action) {
        if (document == null || actorUser == null || action == null) return;
        DocumentActivity activity = DocumentActivity.builder()
                .document(document)
                .actorUser(actorUser)
                .action(action)
                .build();
        documentActivityRepository.save(activity);
    }
}

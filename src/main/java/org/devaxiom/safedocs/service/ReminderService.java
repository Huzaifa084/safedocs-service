package org.devaxiom.safedocs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.enums.DocumentShareStatus;
import org.devaxiom.safedocs.enums.DocumentStatus;
import org.devaxiom.safedocs.mail.EmailService;
import org.devaxiom.safedocs.mail.EmailTemplates;
import org.devaxiom.safedocs.model.Document;
import org.devaxiom.safedocs.model.DocumentShare;
import org.devaxiom.safedocs.repository.DocumentRepository;
import org.devaxiom.safedocs.repository.DocumentShareRepository;
import org.devaxiom.safedocs.repository.FamilyMemberRepository;
import org.devaxiom.safedocs.repository.ReminderLogRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private static final int[] WINDOWS = new int[]{30, 7, 1};

    private final DocumentRepository documentRepository;
    private final DocumentShareRepository documentShareRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final EmailService emailService;

    // Runs every day at 07:30 server time
    @Scheduled(cron = "0 30 7 * * *")
    public void sendUpcomingExpiryReminders() {
        LocalDate today = LocalDate.now();
        LocalDate maxWindow = today.plusDays(maxWindow());
        List<Document> docs = documentRepository.findByStatusAndExpiryDateBetween(
                DocumentStatus.ACTIVE, today, maxWindow);
        log.info("Reminder sweep: found {} documents expiring by {}", docs.size(), maxWindow);
        for (Document doc : docs) {
            for (int days : WINDOWS) {
                sendRemindersFor(doc, days, today);
            }
        }
    }

    private void sendRemindersFor(Document doc, int days, LocalDate today) {
        if (doc.getExpiryDate() == null) return;
        long delta = java.time.temporal.ChronoUnit.DAYS.between(today, doc.getExpiryDate());
        if (delta != days) return;
        Set<String> recipients = new HashSet<>();
        switch (doc.getVisibility()) {
            case PERSONAL -> {
                if (doc.getOwner() != null && doc.getOwner().getEmail() != null) {
                    recipients.add(doc.getOwner().getEmail());
                }
            }
            case FAMILY -> {
                if (doc.getFamily() != null) {
                    familyMemberRepository.findByFamilyIdAndActiveTrue(doc.getFamily().getId())
                            .forEach(m -> {
                                if (m.getUser() != null && m.getUser().getEmail() != null) {
                                    recipients.add(m.getUser().getEmail());
                                }
                            });
                }
            }
            case SHARED -> {
                if (doc.getOwner() != null && doc.getOwner().getEmail() != null) {
                    recipients.add(doc.getOwner().getEmail());
                }
                List<DocumentShare> shares = documentShareRepository.findByDocumentIdAndStatus(doc.getId(), DocumentShareStatus.ACTIVE);
                for (DocumentShare ds : shares) {
                    if (ds.getRecipientEmail() != null) recipients.add(ds.getRecipientEmail());
                }
            }
            default -> {
            }
        }
        recipients.forEach(email -> sendIfNotSent(doc, email, days));
    }

    private void sendIfNotSent(Document doc, String email, int days) {
        String normalized = email.trim().toLowerCase();
        String type = "UPCOMING_%s_DAYS".formatted(days);
        if (reminderLogRepository.existsByDocumentIdAndRecipientEmailAndReminderType(doc.getId(), normalized, type)) {
            return;
        }
        String subject = "Document expiring in %s day%s: %s".formatted(days, days == 1 ? "" : "s", doc.getTitle());
        String body = EmailTemplates.expiryHtml(doc.getTitle(), String.valueOf(doc.getExpiryDate()));
        try {
            emailService.sendEmail(normalized, subject, body);
            reminderLogRepository.save(org.devaxiom.safedocs.model.ReminderLog.builder()
                    .document(doc)
                    .recipientEmail(normalized)
                    .reminderType(type)
                    .expirySnapshot(doc.getExpiryDate())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to send reminder to {} for doc {}: {}", normalized, doc.getId(), ex.getMessage());
        }
    }

    private int maxWindow() {
        int max = 0;
        for (int w : WINDOWS) {
            if (w > max) max = w;
        }
        return max;
    }
}

package org.devaxiom.safedocs.mail;

public final class EmailTemplates {

    private EmailTemplates() {
    }

    public static String expiryHtml(String title, String expiryDate) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #111;">
                  <h2 style="color:#2563eb;">SafeDocs Reminder</h2>
                  <p>The document <strong>%s</strong> is expiring on <strong>%s</strong>.</p>
                  <p>Please review and renew if needed.</p>
                  <p style="font-size:12px;color:#6b7280;">This is an automated reminder from SafeDocs.</p>
                </body>
                </html>
                """.formatted(title, expiryDate);
    }
}

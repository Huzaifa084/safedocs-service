package org.devaxiom.safedocs.storage;

import java.util.UUID;

public final class MediaKeys {
    public static String safeFileName(String name) {
        if (name == null) return "file";
        String s = name.strip().replace("\\", "_").replace("/", "_")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (s.isBlank()) s = "file";
        if (s.length() > 120) s = s.substring(s.length() - 120);
        return s;
    }

    public static String finalKey(UUID ticketPid, String filename) {
        String safe = safeFileName(filename);
        return "tickets/%s/%s-%s".formatted(ticketPid, UUID.randomUUID(), safe);
    }

    public static String finalKey(StorageContext context, UUID entityId, String filename) {
        String safe = safeFileName(filename);
        return "%s/%s/%s-%s".formatted(context.getPrefix(), entityId, UUID.randomUUID(), safe);
    }

    public static String finalKey(StorageContext context, String filename) {
        String safe = safeFileName(filename);
        return "%s/%s-%s".formatted(context.getPrefix(), UUID.randomUUID(), safe);
    }

    private MediaKeys() {
    }
}

package org.devaxiom.safedocs.dto.document;

import java.util.List;
import java.util.UUID;

public record BulkDeleteDocumentsResponse(
        List<UUID> deleted,
        List<Failure> failed
) {
    public record Failure(
            UUID id,
            BulkFailureReason reason
    ) {
    }

    public enum BulkFailureReason {
        NOT_FOUND,
        PERMISSION_DENIED
    }
}

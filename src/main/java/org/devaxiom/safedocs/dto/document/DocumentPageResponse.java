package org.devaxiom.safedocs.dto.document;

import java.util.List;

public record DocumentPageResponse(
        List<DocumentListItem> items,
        int page,
        int size,
        long total
) {
}

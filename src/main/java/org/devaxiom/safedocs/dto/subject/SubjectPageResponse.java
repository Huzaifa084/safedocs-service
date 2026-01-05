package org.devaxiom.safedocs.dto.subject;

import java.util.List;

public record SubjectPageResponse(
        List<SubjectListItem> items,
        int page,
        int size,
        long total
) {
}

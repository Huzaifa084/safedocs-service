package org.devaxiom.safedocs.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.base.BaseResponseEntity;
import org.devaxiom.safedocs.dto.base.ResponseBuilder;
import org.devaxiom.safedocs.dto.subject.CreateSubjectRequest;
import org.devaxiom.safedocs.dto.subject.SubjectListItem;
import org.devaxiom.safedocs.dto.subject.SubjectPageResponse;
import org.devaxiom.safedocs.dto.subject.UpdateSubjectRequest;
import org.devaxiom.safedocs.enums.SubjectScope;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.service.PrincipleUserService;
import org.devaxiom.safedocs.service.SubjectService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;
    private final PrincipleUserService principleUserService;

    @GetMapping
    public BaseResponseEntity<SubjectPageResponse> list(
            @RequestParam(value = "scope") SubjectScope scope,
            @RequestParam(value = "familyId", required = false) String familyId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        User user = requireUser();
        UUID familyPublicId = familyId != null ? parseUuid(familyId, "familyId") : null;
        SubjectPageResponse resp = subjectService.list(user, scope, familyPublicId, page, size);
        return ResponseBuilder.success(resp, "Subjects fetched");
    }

    @PostMapping
    public BaseResponseEntity<SubjectListItem> create(@Valid @RequestBody CreateSubjectRequest request) {
        User user = requireUser();
        SubjectListItem resp = subjectService.create(user, request);
        return ResponseBuilder.success(resp, "Subject created");
    }

    @PutMapping("/{subjectId}")
    public BaseResponseEntity<SubjectListItem> rename(
            @PathVariable("subjectId") String subjectId,
            @Valid @RequestBody UpdateSubjectRequest request
    ) {
        User user = requireUser();
        SubjectListItem resp = subjectService.rename(user, parseUuid(subjectId, "subjectId"), request);
        return ResponseBuilder.success(resp, "Subject updated");
    }

    @DeleteMapping("/{subjectId}")
    public BaseResponseEntity<?> delete(@PathVariable("subjectId") String subjectId) {
        User user = requireUser();
        subjectService.delete(user, parseUuid(subjectId, "subjectId"));
        return ResponseBuilder.success("Subject deleted");
    }

    private User requireUser() {
        return principleUserService.getCurrentUser()
                .orElseThrow(() -> new BadRequestException("Unauthorized"));
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid " + fieldName);
        }
    }
}

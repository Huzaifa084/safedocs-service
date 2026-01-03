package org.devaxiom.safedocs.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.base.BaseResponseEntity;
import org.devaxiom.safedocs.dto.base.ResponseBuilder;
import org.devaxiom.safedocs.dto.permission.PermissionJobCreateRequest;
import org.devaxiom.safedocs.dto.permission.PermissionJobCreateResponse;
import org.devaxiom.safedocs.dto.permission.PermissionJobResponse;
import org.devaxiom.safedocs.dto.permission.PermissionJobUpdateRequest;
import org.devaxiom.safedocs.enums.PermissionJobStatus;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.service.PermissionJobService;
import org.devaxiom.safedocs.service.PrincipleUserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/permissions/jobs")
@RequiredArgsConstructor
public class PermissionJobController {

    private final PermissionJobService permissionJobService;
    private final PrincipleUserService principleUserService;

    @PostMapping
    public BaseResponseEntity<PermissionJobCreateResponse> createJobs(
            @Valid @RequestBody PermissionJobCreateRequest request) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        int queued = permissionJobService.enqueueJobs(request.jobs(), user);
        return ResponseBuilder.success(new PermissionJobCreateResponse(queued), "Jobs queued");
    }

    @GetMapping
    public BaseResponseEntity<List<PermissionJobResponse>> listJobs(
            @RequestParam(value = "status", required = false) PermissionJobStatus status,
            @RequestParam(value = "ownerUserId", required = false) String ownerUserId) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        if (ownerUserId != null && !"me".equalsIgnoreCase(ownerUserId)) {
            throw new BadRequestException("ownerUserId must be 'me'");
        }
        List<PermissionJobResponse> jobs = permissionJobService.listJobs(user, status);
        return ResponseBuilder.success(jobs, "Jobs fetched");
    }

    @PatchMapping("/{jobId}")
    public BaseResponseEntity<PermissionJobResponse> updateJob(
            @PathVariable("jobId") UUID jobId,
            @Valid @RequestBody PermissionJobUpdateRequest request) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        PermissionJobResponse response = permissionJobService.updateJob(jobId, request, user);
        return ResponseBuilder.success(response, "Job updated");
    }
}

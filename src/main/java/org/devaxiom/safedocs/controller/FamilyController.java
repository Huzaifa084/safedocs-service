package org.devaxiom.safedocs.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.base.BaseResponseEntity;
import org.devaxiom.safedocs.dto.base.ResponseBuilder;
import org.devaxiom.safedocs.dto.family.CreateFamilyRequest;
import org.devaxiom.safedocs.dto.family.FamilyMemberResponse;
import org.devaxiom.safedocs.dto.family.FamilyProfileResponse;
import org.devaxiom.safedocs.dto.family.FamilySummaryResponse;
import org.devaxiom.safedocs.dto.family.InviteFamilyMemberRequest;
import org.devaxiom.safedocs.dto.family.UpdateFamilyRequest;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.service.FamilyService;
import org.devaxiom.safedocs.service.PrincipleUserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;
    private final PrincipleUserService principleUserService;

    @GetMapping
    public BaseResponseEntity<List<FamilySummaryResponse>> listFamilies() {
        User user = currentUser();
        return ResponseBuilder.success(familyService.listFamilies(user), "Families fetched");
    }

    @PostMapping
    public BaseResponseEntity<FamilySummaryResponse> create(@Valid @RequestBody CreateFamilyRequest request) {
        User user = currentUser();
        return ResponseBuilder.success(familyService.createFamily(user, request), "Family created");
    }

    @GetMapping("/{familyId}")
    public BaseResponseEntity<FamilyProfileResponse> profile(@PathVariable String familyId) {
        User user = currentUser();
        return ResponseBuilder.success(familyService.getFamilyProfile(parseUuid(familyId), user), "Family profile fetched");
    }

    @PutMapping("/{familyId}")
    public BaseResponseEntity<FamilyProfileResponse> rename(@PathVariable String familyId,
                                                            @Valid @RequestBody UpdateFamilyRequest request) {
        User user = currentUser();
        return ResponseBuilder.success(familyService.renameFamily(parseUuid(familyId), request, user), "Family renamed");
    }

    @GetMapping("/{familyId}/members")
    public BaseResponseEntity<List<FamilyMemberResponse>> members(@PathVariable String familyId) {
        User user = currentUser();
        return ResponseBuilder.success(familyService.listMembers(parseUuid(familyId), user), "Family members fetched");
    }

    @PostMapping("/{familyId}/invite")
    public BaseResponseEntity<FamilyMemberResponse> invite(@PathVariable String familyId,
                                                           @Valid @RequestBody InviteFamilyMemberRequest request) {
        User user = currentUser();
        FamilyMemberResponse member = familyService.invite(user, parseUuid(familyId), request);
        return ResponseBuilder.success(member, "Invite created");
    }

    @PostMapping("/invite/{inviteId}/accept")
    public BaseResponseEntity<FamilyMemberResponse> acceptInvite(@PathVariable("inviteId") String inviteId) {
        User user = currentUser();
        FamilyMemberResponse resp = familyService.acceptInvite(parseUuid(inviteId), user);
        return ResponseBuilder.success(resp, "Invite accepted");
    }

    @PostMapping("/invite/{inviteId}/reject")
    public BaseResponseEntity<?> rejectInvite(@PathVariable("inviteId") String inviteId) {
        User user = currentUser();
        familyService.rejectInvite(parseUuid(inviteId), user);
        return ResponseBuilder.success("Invite rejected");
    }

    @DeleteMapping("/{familyId}/members/{userId}")
    public BaseResponseEntity<?> removeMember(@PathVariable String familyId, @PathVariable("userId") Long userId) {
        User user = currentUser();
        familyService.removeMember(user, parseUuid(familyId), userId);
        return ResponseBuilder.success("Member removed");
    }

    @PostMapping("/{familyId}/leave")
    public BaseResponseEntity<?> leaveFamily(@PathVariable String familyId) {
        User user = currentUser();
        familyService.leaveFamily(user, parseUuid(familyId));
        return ResponseBuilder.success("Left family");
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid invite id");
        }
    }

    private User currentUser() {
        return principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
    }
}

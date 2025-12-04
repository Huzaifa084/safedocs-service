package org.devaxiom.safedocs.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.base.BaseResponseEntity;
import org.devaxiom.safedocs.dto.base.ResponseBuilder;
import org.devaxiom.safedocs.dto.family.FamilyMemberResponse;
import org.devaxiom.safedocs.dto.family.InviteFamilyMemberRequest;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.service.FamilyService;
import org.devaxiom.safedocs.service.PrincipleUserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;
    private final PrincipleUserService principleUserService;

    @GetMapping("/members")
    public BaseResponseEntity<List<FamilyMemberResponse>> members() {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        List<FamilyMemberResponse> members = familyService.listMembers(user);
        return ResponseBuilder.success(members, "Family members fetched");
    }

    @PostMapping("/invite")
    public BaseResponseEntity<FamilyMemberResponse> invite(@Valid @RequestBody InviteFamilyMemberRequest request) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        FamilyMemberResponse member = familyService.invite(user, request);
        return ResponseBuilder.success(member, "Member added to family");
    }

    @PostMapping("/invite/{inviteId}/accept")
    public BaseResponseEntity<FamilyMemberResponse> acceptInvite(@PathVariable("inviteId") String inviteId) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        FamilyMemberResponse resp = familyService.acceptInvite(parseUuid(inviteId), user);
        return ResponseBuilder.success(resp, "Invite accepted");
    }

    @PostMapping("/invite/{inviteId}/reject")
    public BaseResponseEntity<?> rejectInvite(@PathVariable("inviteId") String inviteId) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        familyService.rejectInvite(parseUuid(inviteId), user);
        return ResponseBuilder.success("Invite rejected");
    }

    @DeleteMapping("/members/{userId}")
    public BaseResponseEntity<?> removeMember(@PathVariable("userId") Long userId) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        familyService.removeMember(user, userId);
        return ResponseBuilder.success("Member removed");
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid invite id");
        }
    }
}

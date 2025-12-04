package org.devaxiom.safedocs.service;

import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.family.FamilyMemberResponse;
import org.devaxiom.safedocs.dto.family.InviteFamilyMemberRequest;
import org.devaxiom.safedocs.enums.FamilyInviteStatus;
import org.devaxiom.safedocs.enums.FamilyRole;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.exception.ResourceNotFoundException;
import org.devaxiom.safedocs.model.Family;
import org.devaxiom.safedocs.model.FamilyInvite;
import org.devaxiom.safedocs.model.FamilyMember;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.repository.FamilyInviteRepository;
import org.devaxiom.safedocs.repository.FamilyMemberRepository;
import org.devaxiom.safedocs.repository.FamilyRepository;
import org.devaxiom.safedocs.repository.UserRepository;
import org.devaxiom.safedocs.mail.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final FamilyInviteRepository familyInviteRepository;
    private final EmailService emailService;

    @Transactional
    public Family ensureFamilyForUser(User user) {
        return familyMemberRepository.findByUserIdAndActiveTrue(user.getId())
                .map(FamilyMember::getFamily)
                .orElseGet(() -> createDefaultFamily(user));
    }

    @Transactional
    public List<FamilyMemberResponse> listMembers(User currentUser) {
        Family family = ensureFamilyForUser(currentUser);
        return familyMemberRepository.findByFamilyIdAndActiveTrue(family.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FamilyMemberResponse invite(User currentUser, InviteFamilyMemberRequest req) {
        Family family = ensureFamilyForUser(currentUser);
        // Only head can invite
        boolean isHead = familyMemberRepository.findByFamilyIdAndRoleAndActiveTrue(family.getId(), FamilyRole.HEAD)
                .stream()
                .anyMatch(m -> Objects.equals(m.getUser().getId(), currentUser.getId()));
        if (!isHead) throw new BadRequestException("Only family head can invite members");

        String email = req.email().trim().toLowerCase();
        if (familyMemberRepository.findByFamilyIdAndActiveTrue(family.getId())
                .stream()
                .anyMatch(m -> email.equalsIgnoreCase(m.getUser().getEmail()))) {
            throw new BadRequestException("User already in family");
        }

        if (familyInviteRepository.existsByFamilyIdAndEmailAndStatus(family.getId(), email, FamilyInviteStatus.PENDING)) {
            throw new BadRequestException("Invite already pending for this email");
        }

        FamilyInvite invite = FamilyInvite.builder()
                .family(family)
                .invitedBy(currentUser)
                .email(email)
                .status(FamilyInviteStatus.PENDING)
                .build();
        invite = familyInviteRepository.save(invite);
        sendInviteEmail(invite);
        return new FamilyMemberResponse(null, invite.getEmail(), null, FamilyRole.MEMBER, false);
    }

    @Transactional
    public FamilyMemberResponse acceptInvite(UUID inviteId, User currentUser) {
        FamilyInvite invite = familyInviteRepository.findByPublicId(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));
        if (invite.getStatus() != FamilyInviteStatus.PENDING) {
            throw new BadRequestException("Invite is not pending");
        }
        if (!invite.getEmail().equalsIgnoreCase(currentUser.getEmail())) {
            throw new BadRequestException("Invite email does not match your account");
        }
        Family family = invite.getFamily();
        if (familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(family.getId(), currentUser.getId()).isPresent()) {
            invite.setStatus(FamilyInviteStatus.ACCEPTED);
            familyInviteRepository.save(invite);
            return toResponse(currentUser, FamilyRole.MEMBER, true);
        }
        FamilyMember member = FamilyMember.builder()
                .family(family)
                .user(currentUser)
                .role(FamilyRole.MEMBER)
                .active(true)
                .build();
        familyMemberRepository.save(member);
        invite.setStatus(FamilyInviteStatus.ACCEPTED);
        familyInviteRepository.save(invite);
        return toResponse(member);
    }

    @Transactional
    public void rejectInvite(UUID inviteId, User currentUser) {
        FamilyInvite invite = familyInviteRepository.findByPublicId(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));
        if (invite.getStatus() != FamilyInviteStatus.PENDING) {
            throw new BadRequestException("Invite is not pending");
        }
        if (!invite.getEmail().equalsIgnoreCase(currentUser.getEmail())) {
            throw new BadRequestException("Invite email does not match your account");
        }
        invite.setStatus(FamilyInviteStatus.REJECTED);
        familyInviteRepository.save(invite);
    }

    @Transactional
    public void removeMember(User currentUser, Long memberUserId) {
        Family family = ensureFamilyForUser(currentUser);
        boolean isHead = familyMemberRepository.findByFamilyIdAndRoleAndActiveTrue(family.getId(), FamilyRole.HEAD)
                .stream()
                .anyMatch(m -> Objects.equals(m.getUser().getId(), currentUser.getId()));
        if (!isHead) throw new BadRequestException("Only family head can remove members");
        if (Objects.equals(memberUserId, currentUser.getId())) {
            throw new BadRequestException("Head cannot remove themselves");
        }
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(family.getId(), memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        member.setActive(false);
        familyMemberRepository.save(member);
    }

    private Family createDefaultFamily(User user) {
        Family family = Family.builder()
                .name("Family of " + user.getFullName())
                .build();
        family = familyRepository.save(family);
        FamilyMember head = FamilyMember.builder()
                .family(family)
                .user(user)
                .role(FamilyRole.HEAD)
                .active(true)
                .build();
        familyMemberRepository.save(head);
        return family;
    }

    private FamilyMemberResponse toResponse(FamilyMember member) {
        return new FamilyMemberResponse(
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getFullName(),
                member.getRole(),
                Boolean.TRUE.equals(member.getActive())
        );
    }

    private FamilyMemberResponse toResponse(User user, FamilyRole role, boolean active) {
        return new FamilyMemberResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                role,
                active
        );
    }

    private void sendInviteEmail(FamilyInvite invite) {
        String subject = "SafeDocs: Family invite";
        String body = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #111;">
                  <p>You have been invited to join a family on SafeDocs.</p>
                  <p>Invite code: <strong>%s</strong></p>
                  <p>Please open the app and accept the invite.</p>
                </body>
                </html>
                """.formatted(invite.getPublicId());
        try {
            emailService.sendEmail(invite.getEmail(), subject, body);
        } catch (Exception ignored) {
        }
    }
}

package org.devaxiom.safedocs.service;

import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.family.CreateFamilyRequest;
import org.devaxiom.safedocs.dto.family.FamilyMemberResponse;
import org.devaxiom.safedocs.dto.family.FamilyProfileResponse;
import org.devaxiom.safedocs.dto.family.FamilySummaryResponse;
import org.devaxiom.safedocs.dto.family.InviteFamilyMemberRequest;
import org.devaxiom.safedocs.dto.family.UpdateFamilyRequest;
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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final FamilyInviteRepository familyInviteRepository;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<FamilySummaryResponse> listFamilies(User user) {
        List<FamilyMember> memberships = familyMemberRepository.findByUserIdAndActiveTrue(user.getId());
        return memberships.stream()
                .map(m -> {
                    int memberCount = familyMemberRepository.findByFamilyIdAndActiveTrue(m.getFamily().getId()).size();
                    return new FamilySummaryResponse(
                            m.getFamily().getPublicId(),
                            m.getFamily().getName(),
                            m.getRole(),
                            memberCount
                    );
                })
                .sorted(Comparator.comparing(FamilySummaryResponse::familyName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public FamilySummaryResponse createFamily(User user, CreateFamilyRequest req) {
        String name = req.name().trim();
        if (name.isBlank()) {
            throw new BadRequestException("Family name is required");
        }
        Family family = Family.builder()
                .name(name)
                .build();
        family = familyRepository.save(family);
        FamilyMember head = FamilyMember.builder()
                .family(family)
                .user(user)
                .role(FamilyRole.HEAD)
                .active(true)
                .build();
        familyMemberRepository.save(head);
        return new FamilySummaryResponse(family.getPublicId(), family.getName(), FamilyRole.HEAD, 1);
    }

    @Transactional(readOnly = true)
    public FamilyProfileResponse getFamilyProfile(UUID familyPublicId, User user) {
        Family family = requireMembership(familyPublicId, user);
        List<FamilyMember> members = familyMemberRepository.findByFamilyIdAndActiveTrue(family.getId());
        FamilyMember head = members.stream()
                .filter(m -> m.getRole() == FamilyRole.HEAD)
                .findFirst()
                .orElse(null);
        return new FamilyProfileResponse(
                family.getPublicId(),
                family.getName(),
                head != null ? head.getUser().getPublicId() : null,
                head != null ? head.getUser().getFullName() : null,
                members.stream().map(this::toResponse).toList()
        );
    }

    @Transactional
    public FamilyProfileResponse renameFamily(UUID familyPublicId, UpdateFamilyRequest req, User user) {
        FamilyMember membership = requireMembershipWithRole(familyPublicId, user, FamilyRole.HEAD);
        Family family = membership.getFamily();
        family.setName(req.name().trim());
        familyRepository.save(family);
        return getFamilyProfile(familyPublicId, user);
    }

    @Transactional
    public List<FamilyMemberResponse> listMembers(UUID familyPublicId, User currentUser) {
        Family family = requireMembership(familyPublicId, currentUser);
        return familyMemberRepository.findByFamilyIdAndActiveTrue(family.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FamilyMemberResponse invite(User currentUser, UUID familyPublicId, InviteFamilyMemberRequest req) {
        FamilyMember headMembership = requireMembershipWithRole(familyPublicId, currentUser, FamilyRole.HEAD);
        Family family = headMembership.getFamily();

        String email = req.email().trim().toLowerCase();
        boolean alreadyMember = familyMemberRepository.findByFamilyIdAndActiveTrue(family.getId())
                .stream()
                .anyMatch(m -> email.equalsIgnoreCase(m.getUser().getEmail()));
        if (alreadyMember) {
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
        if (familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(family.getId(), currentUser.getId()).isEmpty()) {
            FamilyMember member = FamilyMember.builder()
                    .family(family)
                    .user(currentUser)
                    .role(FamilyRole.MEMBER)
                    .active(true)
                    .build();
            familyMemberRepository.save(member);
        }
        invite.setStatus(FamilyInviteStatus.ACCEPTED);
        familyInviteRepository.save(invite);
        return toResponse(currentUser, FamilyRole.MEMBER, true);
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
    public void removeMember(User currentUser, UUID familyPublicId, Long memberUserId) {
        FamilyMember headMembership = requireMembershipWithRole(familyPublicId, currentUser, FamilyRole.HEAD);
        if (Objects.equals(memberUserId, currentUser.getId())) {
            throw new BadRequestException("Head cannot remove themselves. Transfer head role first.");
        }
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(headMembership.getFamily().getId(), memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        member.setActive(false);
        familyMemberRepository.save(member);
    }

    @Transactional
    public void leaveFamily(User currentUser, UUID familyPublicId) {
        Family family = requireMembership(familyPublicId, currentUser);
        FamilyMember membership = familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(family.getId(), currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));
        boolean isHead = membership.getRole() == FamilyRole.HEAD;
        List<FamilyMember> activeMembers = familyMemberRepository.findByFamilyIdAndActiveTrue(family.getId());
        if (isHead && activeMembers.stream().anyMatch(m -> !Objects.equals(m.getUser().getId(), currentUser.getId()))) {
            throw new BadRequestException("Head must transfer role or remove members before leaving");
        }
        membership.setActive(false);
        familyMemberRepository.save(membership);
    }

    private Family requireMembership(UUID familyPublicId, User user) {
        Family family = familyRepository.findByPublicId(familyPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Family not found"));
        boolean member = familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(family.getId(), user.getId()).isPresent();
        if (!member) {
            throw new BadRequestException("Not a member of this family");
        }
        return family;
    }

    private FamilyMember requireMembershipWithRole(UUID familyPublicId, User user, FamilyRole role) {
        Family family = familyRepository.findByPublicId(familyPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Family not found"));
        return familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(family.getId(), user.getId())
                .filter(m -> m.getRole() == role)
                .orElseThrow(() -> new BadRequestException("Insufficient permissions for this family"));
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

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid " + fieldName);
        }
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

package org.devaxiom.safedocs.service;

import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.family.CreateFamilyRequest;
import org.devaxiom.safedocs.dto.family.FamilyMemberResponse;
import org.devaxiom.safedocs.dto.family.FamilyProfileResponse;
import org.devaxiom.safedocs.dto.family.FamilySummaryResponse;
import org.devaxiom.safedocs.dto.family.FamilyInviteResponse;
import org.devaxiom.safedocs.dto.family.InviteFamilyMemberRequest;
import org.devaxiom.safedocs.dto.family.UpdateFamilyRequest;
import org.devaxiom.safedocs.enums.DocumentStatus;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.devaxiom.safedocs.enums.FamilyInviteStatus;
import org.devaxiom.safedocs.enums.FamilyRole;
import org.devaxiom.safedocs.enums.PermissionJobAction;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.exception.ResourceNotFoundException;
import org.devaxiom.safedocs.model.Document;
import org.devaxiom.safedocs.model.Family;
import org.devaxiom.safedocs.model.FamilyInvite;
import org.devaxiom.safedocs.model.FamilyMember;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.repository.DocumentRepository;
import org.devaxiom.safedocs.repository.FamilyInviteRepository;
import org.devaxiom.safedocs.repository.FamilyMemberRepository;
import org.devaxiom.safedocs.repository.FamilyRepository;
import org.devaxiom.safedocs.repository.UserRepository;
import org.devaxiom.safedocs.mail.EmailService;
import org.springframework.data.domain.Sort;
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
    private final DocumentRepository documentRepository;
    private final PermissionJobService permissionJobService;
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

        // Enqueue GRANT jobs for all FAMILY documents in this family
        enqueueJobsForFamilyDocs(family, normalizeEmail(currentUser.getEmail()), PermissionJobAction.GRANT);

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

    @Transactional(readOnly = true)
    public List<FamilyInviteResponse> listPendingInvites(User currentUser) {
        String email = normalizeEmail(currentUser.getEmail());
        return familyInviteRepository.findByEmailAndStatus(email, FamilyInviteStatus.PENDING)
                .stream()
                .map(inv -> new FamilyInviteResponse(
                        inv.getPublicId(),
                        inv.getFamily() != null ? inv.getFamily().getPublicId() : null,
                        inv.getFamily() != null ? inv.getFamily().getName() : null,
                        inv.getEmail(),
                        inv.getInvitedBy() != null ? inv.getInvitedBy().getFullName() : null,
                        inv.getInvitedBy() != null ? inv.getInvitedBy().getEmail() : null,
                        inv.getStatus()
                ))
                .toList();
    }

    @Transactional
    public void removeMember(User currentUser, UUID familyPublicId, Long memberUserId) {
        FamilyMember headMembership = requireMembershipWithRole(familyPublicId, currentUser, FamilyRole.HEAD);
        if (Objects.equals(memberUserId, currentUser.getId())) {
            throw new BadRequestException("Head cannot remove themselves. Transfer head role first.");
        }
        FamilyMember member = familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(headMembership.getFamily().getId(), memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        // Enqueue REVOKE jobs for all FAMILY documents for this removed member
        enqueueJobsForFamilyDocs(headMembership.getFamily(), normalizeEmail(member.getUser().getEmail()), PermissionJobAction.REVOKE);

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

        // Enqueue REVOKE jobs for all FAMILY documents for this leaving member
        enqueueJobsForFamilyDocs(family, normalizeEmail(currentUser.getEmail()), PermissionJobAction.REVOKE);

        membership.setActive(false);
        familyMemberRepository.save(membership);
    }

    @Transactional
    public void deleteFamily(User currentUser, UUID familyPublicId) {
        FamilyMember headMembership = requireMembershipWithRole(familyPublicId, currentUser, FamilyRole.HEAD);
        Family family = headMembership.getFamily();

        // Snapshot active members before deletion (used to enqueue REVOKE jobs)
        List<FamilyMember> activeMembers = familyMemberRepository.findByFamilyIdAndActiveTrue(family.getId());

        // remove members (all states)
        List<FamilyMember> members = familyMemberRepository.findByFamilyId(family.getId());
        familyMemberRepository.deleteAll(members);
        familyMemberRepository.flush();

        // remove invites
        List<FamilyInvite> invites = familyInviteRepository.findByFamilyId(family.getId());
        familyInviteRepository.deleteAll(invites);
        familyInviteRepository.flush();

        // Revoke access for all family members across all FAMILY docs
        List<Document> docs = documentRepository.findByFamilyId(family.getId());
        for (Document d : docs) {
            if (d.getVisibility() != DocumentVisibility.FAMILY) continue;
            if (d.getStatus() != DocumentStatus.ACTIVE) continue;
            for (FamilyMember member : activeMembers) {
                if (member.getUser() == null) continue;
                String email = normalizeEmail(member.getUser().getEmail());
                if (email == null) continue;
                permissionJobService.enqueueJob(d, d.getOwner(), email, PermissionJobAction.REVOKE, family);
            }
        }

        // Convert family docs back to PERSONAL ownership and detach FK
        docs.forEach(d -> {
            if (d.getVisibility() == DocumentVisibility.FAMILY) {
                d.setVisibility(DocumentVisibility.PERSONAL);
            }
            d.setFamily(null);
        });
        documentRepository.saveAll(docs);
        documentRepository.flush();

        familyRepository.delete(family);
    }

    private void enqueueJobsForFamilyDocs(Family family, String targetEmail, PermissionJobAction action) {
        if (family == null) return;
        if (targetEmail == null || targetEmail.isBlank()) return;
        List<Document> docs = documentRepository.findByFamilyId(family.getId());
        for (Document doc : docs) {
            if (doc.getVisibility() != DocumentVisibility.FAMILY) continue;
            if (doc.getStatus() != DocumentStatus.ACTIVE) continue;
            if (doc.getOwner() == null) continue;
            permissionJobService.enqueueJob(doc, doc.getOwner(), targetEmail, action, family);
        }
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

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private void sendInviteEmail(FamilyInvite invite) {
        String subject = "You’re invited to a SafeDocs family";
        String inviterName = invite.getInvitedBy() != null && invite.getInvitedBy().getFullName() != null
                ? invite.getInvitedBy().getFullName()
                : "A SafeDocs user";
        String body = """
                <html>
                <body style="margin:0;padding:0;background:#f6f8fb;font-family:Arial,sans-serif;color:#111;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:32px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="560" cellspacing="0" cellpadding="0" style="background:#ffffff;border-radius:12px;box-shadow:0 6px 18px rgba(17,24,39,0.08);overflow:hidden;">
                          <tr>
                            <td style="background:#1d4ed8;color:#fff;padding:18px 24px;font-size:18px;font-weight:700;">SafeDocs</td>
                          </tr>
                          <tr>
                            <td style="padding:28px 24px 16px 24px;">
                              <h2 style="margin:0 0 12px 0;font-size:22px;color:#0f172a;">You’re invited to join a family</h2>
                              <p style="margin:0 0 10px 0;font-size:15px;color:#334155;">
                                %s has invited you to join their SafeDocs family workspace.
                              </p>
                              <p style="margin:0 0 10px 0;font-size:15px;color:#334155;">
                                Use this invite code to accept in the app:
                              </p>
                              <div style="margin:16px 0;padding:14px 16px;border:1px dashed #1d4ed8;border-radius:10px;background:#eff6ff;font-size:16px;font-weight:700;color:#1d4ed8;letter-spacing:0.5px;text-align:center;">
                                %s
                              </div>
                              <p style="margin:0 0 12px 0;font-size:15px;color:#334155;">
                                Open SafeDocs, go to <strong>Family</strong> &gt; <strong>Invites</strong>, and paste the code to accept.
                              </p>
                              <p style="margin:0;font-size:13px;color:#94a3b8;">If you weren’t expecting this, you can ignore this email.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 24px 24px 24px;font-size:12px;color:#94a3b8;text-align:right;">
                              SafeDocs · Securely share and protect your documents
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(inviterName, invite.getPublicId());
        try {
            emailService.sendEmail(invite.getEmail(), subject, body);
        } catch (Exception ignored) {
        }
    }
}

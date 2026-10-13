package com.classhack.service;

import com.classhack.dto.TeamCreateRequest;
import com.classhack.dto.TeamMemberResponse;
import com.classhack.dto.TeamResponse;
import com.classhack.entity.*;
import com.classhack.entity.enums.Role;
import com.classhack.entity.enums.TeamStatus;
import com.classhack.exception.BadRequestException;
import com.classhack.exception.DuplicateResourceException;
import com.classhack.exception.ResourceNotFoundException;
import com.classhack.exception.UnauthorizedException;
import com.classhack.repository.*;
import com.classhack.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    public static final String STUDENT_ALREADY_IN_TEAM_MESSAGE = "This student already included in another team";

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final HackathonRepository hackathonRepository;
    private final StudentRepository studentRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuthService authService;

    @Transactional
    public TeamResponse createTeam(Long hackathonId, TeamCreateRequest req, Long studentLeaderId) {
        Hackathon hackathon = hackathonRepository.findByIdAndDeletedFalse(hackathonId)
                .orElseThrow(() -> new ResourceNotFoundException("Hackathon not found with ID: " + hackathonId));

        // Rule 9: No new team can be created after the registration deadline.
        if (!hackathon.isRegistrationOpen()) {
            throw new BadRequestException("Hackathon registration is closed. The deadline has passed or it is not yet open.");
        }

        Student leader = studentRepository.findById(studentLeaderId)
                .orElseThrow(() -> new ResourceNotFoundException("Student leader record not found."));

        if (!leader.isActive()) {
            throw new BadRequestException("Your student account is deactivated. Please contact your administrator.");
        }

        // Rule 4: Team name must be unique within the same hackathon
        String trimmedTeamName = req.getTeamName().trim();
        if (teamRepository.existsByTeamNameIgnoreCaseAndHackathonIdAndDeletedFalse(trimmedTeamName, hackathon.getId())) {
            throw new DuplicateResourceException("A team named '" + trimmedTeamName + "' is already registered for this hackathon.");
        }

        // Gather all unique student member IDs (excluding or deduplicating leader)
        Set<Long> uniqueMemberIds = new LinkedHashSet<>();
        if (req.getMemberStudentIds() != null) {
            for (Long id : req.getMemberStudentIds()) {
                if (id != null) {
                    if (id.equals(leader.getId())) {
                        continue; // leader is already being added as leader
                    }
                    // Rule 10: Cannot add duplicate student to the same team
                    if (uniqueMemberIds.contains(id)) {
                        throw new BadRequestException("Cannot add duplicate student member to the team.");
                    }
                    uniqueMemberIds.add(id);
                }
            }
        }

        // Rule 3: Team cannot exceed hackathon maximum team size (leader + other members)
        int totalRequestedSize = 1 + uniqueMemberIds.size();
        if (totalRequestedSize > hackathon.getMaximumTeamSize()) {
            throw new BadRequestException("Team size of " + totalRequestedSize + " exceeds the maximum allowed team size of " + hackathon.getMaximumTeamSize() + " for this hackathon.");
        }

        // Acquire deterministic locks on all participant student IDs to safely prevent concurrent race conditions
        List<Long> allParticipantIds = new ArrayList<>();
        allParticipantIds.add(leader.getId());
        allParticipantIds.addAll(uniqueMemberIds);
        Collections.sort(allParticipantIds);
        for (Long pId : allParticipantIds) {
            studentRepository.findByIdWithLock(pId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + pId));
        }

        // Rule 2 & Scenario A/B/C: Check whether student leader is already in a team (leader or member) for this hackathon
        if (teamMemberRepository.isStudentInHackathonTeam(leader.getId(), hackathon.getId())) {
            throw new BadRequestException(STUDENT_ALREADY_IN_TEAM_MESSAGE);
        }

        // Create the team
        Team team = Team.builder()
                .hackathon(hackathon)
                .teamName(trimmedTeamName)
                .teamLeader(leader)
                .status(TeamStatus.REGISTERED)
                .deleted(false)
                .build();

        Team savedTeam = teamRepository.save(team);

        // Rule 6: Team leader must be a member of the team
        TeamMember leaderMember = TeamMember.builder()
                .team(savedTeam)
                .student(leader)
                .role("Leader")
                .build();
        teamMemberRepository.save(leaderMember);
        savedTeam.getMembers().add(leaderMember);

        // Add additional members
        for (Long memberId : uniqueMemberIds) {
            // Rule 5: Only existing registered students can be added
            Student memberStudent = studentRepository.findById(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student member not found with ID: " + memberId));

            if (!memberStudent.isActive()) {
                throw new BadRequestException("Student " + memberStudent.getFullName() + " (" + memberStudent.getStudentId() + ") is inactive and cannot be added.");
            }

            // Rule 2 & Scenario A/B: A student cannot belong to two teams in the same hackathon
            if (teamMemberRepository.isStudentInHackathonTeam(memberStudent.getId(), hackathon.getId())) {
                throw new BadRequestException(STUDENT_ALREADY_IN_TEAM_MESSAGE);
            }

            TeamMember member = TeamMember.builder()
                    .team(savedTeam)
                    .student(memberStudent)
                    .role("Member")
                    .build();
            teamMemberRepository.save(member);
            savedTeam.getMembers().add(member);

            // Send notification to added member
            notificationRepository.save(Notification.builder()
                    .student(memberStudent)
                    .title("Added to Team: " + savedTeam.getTeamName())
                    .message("You were added to team '" + savedTeam.getTeamName() + "' by " + leader.getFullName() + " for hackathon '" + hackathon.getName() + "'.")
                    .readStatus(false)
                    .build());
        }

        auditLogRepository.save(AuditLog.builder()
                .action("TEAM_CREATED")
                .performedBy(leader.getEmail())
                .details("Team '" + savedTeam.getTeamName() + "' created for hackathon '" + hackathon.getName() + "' by leader " + leader.getFullName())
                .build());

        return mapToTeamResponse(savedTeam);
    }

    @Transactional
    public TeamResponse updateTeamStatus(Long teamId, TeamStatus newStatus) {
        CustomUserDetails user = authService.getCurrentUser();
        if (user == null || user.getRole() != Role.ROLE_ADMIN) {
            throw new UnauthorizedException("Only administrators can update team status.");
        }

        Team team = teamRepository.findByIdAndDeletedFalse(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with ID: " + teamId));

        TeamStatus oldStatus = team.getStatus();
        team.setStatus(newStatus);
        Team updated = teamRepository.save(team);

        auditLogRepository.save(AuditLog.builder()
                .action("TEAM_STATUS_UPDATED")
                .performedBy(user.getEmail())
                .details("Team '" + team.getTeamName() + "' status changed from " + oldStatus + " to " + newStatus)
                .build());

        // Notify all team members in-app (Feature 46)
        List<TeamMember> members = teamMemberRepository.findByTeamId(team.getId());
        String notificationMessage = switch (newStatus) {
            case SHORTLISTED -> "🎉 Great news! Your team '" + team.getTeamName() + "' has been SHORTLISTED for " + team.getHackathon().getName() + "!";
            case NEXT_ROUND -> "🚀 Congratulations! Your team '" + team.getTeamName() + "' advanced to NEXT ROUND in " + team.getHackathon().getName() + "!";
            case FINALIST -> "🥈 Outstanding! Your team '" + team.getTeamName() + "' is now a FINALIST in " + team.getHackathon().getName() + "!";
            case WINNER -> "🏆 CONGRATULATIONS! Your team '" + team.getTeamName() + "' has WON in " + team.getHackathon().getName() + "!";
            case ELIMINATED -> "Update: Your team '" + team.getTeamName() + "' status has been set to Eliminated for " + team.getHackathon().getName() + ".";
            case WITHDRAWN -> "Update: Your team '" + team.getTeamName() + "' status has been set to Withdrawn for " + team.getHackathon().getName() + ".";
            default -> "Update: Your team '" + team.getTeamName() + "' status is now " + newStatus + ".";
        };

        for (TeamMember m : members) {
            notificationRepository.save(Notification.builder()
                    .student(m.getStudent())
                    .title("Team Status Update: " + newStatus)
                    .message(notificationMessage)
                    .readStatus(false)
                    .build());
        }

        return mapToTeamResponse(updated);
    }

    @Transactional
    public TeamResponse addTeamMember(Long teamId, Long studentId) {
        CustomUserDetails currentUser = authService.getCurrentUser();
        Team team = teamRepository.findByIdAndDeletedFalse(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with ID: " + teamId));

        // If action is by student, verify they are leader and registration is still open
        if (currentUser.getRole() == Role.ROLE_STUDENT) {
            if (!team.getTeamLeader().getId().equals(currentUser.getId())) {
                throw new UnauthorizedException("Only the team leader can add members.");
            }
            if (!team.getHackathon().isRegistrationOpen()) {
                throw new BadRequestException("Cannot add members after registration deadline.");
            }
        }

        List<TeamMember> currentMembers = teamMemberRepository.findByTeamId(teamId);
        if (currentMembers.size() >= team.getHackathon().getMaximumTeamSize()) {
            throw new BadRequestException("Team has already reached the maximum size of " + team.getHackathon().getMaximumTeamSize());
        }

        // Lock the student row to prevent concurrent duplicate team additions
        Student student = studentRepository.findByIdWithLock(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));

        if (!student.isActive()) {
            throw new BadRequestException("Cannot add inactive student.");
        }

        if (teamMemberRepository.isStudentInHackathonTeam(student.getId(), team.getHackathon().getId())) {
            throw new BadRequestException(STUDENT_ALREADY_IN_TEAM_MESSAGE);
        }

        TeamMember member = TeamMember.builder()
                .team(team)
                .student(student)
                .role("Member")
                .build();
        teamMemberRepository.save(member);

        notificationRepository.save(Notification.builder()
                .student(student)
                .title("Added to Team: " + team.getTeamName())
                .message("You were added to team '" + team.getTeamName() + "' for hackathon '" + team.getHackathon().getName() + "'.")
                .readStatus(false)
                .build());

        return mapToTeamResponse(team);
    }

    @Transactional
    public TeamResponse removeTeamMember(Long teamId, Long studentId) {
        CustomUserDetails currentUser = authService.getCurrentUser();
        Team team = teamRepository.findByIdAndDeletedFalse(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with ID: " + teamId));

        if (currentUser.getRole() == Role.ROLE_STUDENT) {
            if (!team.getTeamLeader().getId().equals(currentUser.getId())) {
                throw new UnauthorizedException("Only the team leader or an admin can remove members.");
            }
            if (!team.getHackathon().isRegistrationOpen()) {
                throw new BadRequestException("Cannot remove members after registration deadline.");
            }
        }

        // Leader cannot be removed
        if (team.getTeamLeader().getId().equals(studentId)) {
            throw new BadRequestException("The team leader cannot be removed from the team.");
        }

        TeamMember member = teamMemberRepository.findByTeamIdAndStudentId(teamId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student is not a member of this team."));

        teamMemberRepository.delete(member);

        return mapToTeamResponse(team);
    }

    @Transactional
    public void deleteTeam(Long teamId) {
        CustomUserDetails currentUser = authService.getCurrentUser();
        Team team = teamRepository.findByIdAndDeletedFalse(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with ID: " + teamId));

        if (currentUser.getRole() == Role.ROLE_STUDENT) {
            if (!team.getTeamLeader().getId().equals(currentUser.getId())) {
                throw new UnauthorizedException("Only the team leader or an admin can delete a team.");
            }
            if (!team.getHackathon().isRegistrationOpen()) {
                throw new BadRequestException("Cannot delete team after registration deadline has passed.");
            }
        }

        // Soft deletion as required: do NOT delete student master records!
        team.setDeleted(true);
        teamRepository.save(team);

        auditLogRepository.save(AuditLog.builder()
                .action("TEAM_DELETED")
                .performedBy(currentUser.getEmail())
                .details("Soft deleted team '" + team.getTeamName() + "' from hackathon '" + team.getHackathon().getName() + "'")
                .build());
    }

    public List<TeamResponse> getTeamsByHackathon(Long hackathonId) {
        CustomUserDetails currentUser = authService.getCurrentUser();
        if (currentUser.getRole() == Role.ROLE_STUDENT) {
            // Privacy rule: Students must NOT see other teams!
            Optional<Team> studentTeam = teamRepository.findTeamByStudentAndHackathon(currentUser.getId(), hackathonId);
            return studentTeam.map(t -> List.of(mapToTeamResponse(t))).orElseGet(Collections::emptyList);
        }

        return teamRepository.findByHackathonIdAndDeletedFalseOrderByCreatedAtDesc(hackathonId).stream()
                .map(this::mapToTeamResponse)
                .collect(Collectors.toList());
    }

    public TeamResponse getTeamById(Long teamId) {
        CustomUserDetails currentUser = authService.getCurrentUser();
        Team team = teamRepository.findByIdAndDeletedFalse(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with ID: " + teamId));

        if (currentUser.getRole() == Role.ROLE_STUDENT) {
            // Ensure student belongs to this team
            boolean isMember = teamMemberRepository.findByTeamIdAndStudentId(teamId, currentUser.getId()).isPresent();
            if (!isMember) {
                throw new UnauthorizedException("Access Denied: You can only view your own team details.");
            }
        }

        return mapToTeamResponse(team);
    }

    public List<TeamResponse> getStudentTeams(Long studentId) {
        CustomUserDetails currentUser = authService.getCurrentUser();
        if (currentUser.getRole() == Role.ROLE_STUDENT && !currentUser.getId().equals(studentId)) {
            throw new UnauthorizedException("Access Denied: You cannot view another student's teams.");
        }

        return teamRepository.findTeamsByStudentId(studentId).stream()
                .map(this::mapToTeamResponse)
                .collect(Collectors.toList());
    }

    public TeamResponse mapToTeamResponse(Team team) {
        List<TeamMember> members = teamMemberRepository.findByTeamId(team.getId());
        List<TeamMemberResponse> memberResponses = members.stream()
                .map(m -> TeamMemberResponse.builder()
                        .id(m.getId())
                        .studentDbId(m.getStudent().getId())
                        .studentId(m.getStudent().getStudentId())
                        .fullName(m.getStudent().getFullName())
                        .department(m.getStudent().getDepartment())
                        .section(m.getStudent().getSection())
                        .year(m.getStudent().getYear())
                        .role(m.getRole())
                        .email(m.getStudent().getEmail())
                        .phone(m.getStudent().getPhone())
                        .build())
                .collect(Collectors.toList());

        return TeamResponse.builder()
                .id(team.getId())
                .teamName(team.getTeamName())
                .hackathonId(team.getHackathon().getId())
                .hackathonName(team.getHackathon().getName())
                .hackathonMode(team.getHackathon().getMode().name())
                .hackathonVenue(team.getHackathon().getVenue())
                .maximumTeamSize(team.getHackathon().getMaximumTeamSize())
                .teamLeaderId(team.getTeamLeader().getId())
                .teamLeaderName(team.getTeamLeader().getFullName())
                .teamLeaderStudentId(team.getTeamLeader().getStudentId())
                .status(team.getStatus())
                .memberCount(members.size())
                .members(memberResponses)
                .createdAt(team.getCreatedAt())
                .build();
    }
}

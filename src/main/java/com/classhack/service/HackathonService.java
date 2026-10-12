package com.classhack.service;

import com.classhack.dto.HackathonCreateRequest;
import com.classhack.dto.HackathonResponse;
import com.classhack.entity.AuditLog;
import com.classhack.entity.Hackathon;
import com.classhack.entity.Team;
import com.classhack.entity.enums.HackathonMode;
import com.classhack.entity.enums.HackathonStatus;
import com.classhack.exception.BadRequestException;
import com.classhack.exception.DuplicateHackathonException;
import com.classhack.exception.ResourceNotFoundException;
import com.classhack.repository.AuditLogRepository;
import com.classhack.repository.HackathonRepository;
import com.classhack.repository.TeamMemberRepository;
import com.classhack.repository.TeamRepository;
import com.classhack.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HackathonService {

    private final HackathonRepository hackathonRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuthService authService;

    @Transactional
    public HackathonResponse createHackathon(HackathonCreateRequest req) {
        validateHackathonRequest(req);

        // Check if another hackathon with the same name already exists
        Optional<Hackathon> existingOpt = hackathonRepository.findFirstByNameIgnoreCaseAndDeletedFalse(req.getName().trim());
        if (existingOpt.isPresent()) {
            Hackathon existing = existingOpt.get();
            if (!Boolean.TRUE.equals(req.getConfirmDuplicate())) {
                throw new DuplicateHackathonException(existing.getName(), "another admin");
            }
        }

        CustomUserDetails admin = authService.getCurrentUser();
        String adminName = admin != null ? admin.getFullName() + " (" + admin.getEmail() + ")" : "Admin";

        Hackathon hackathon = Hackathon.builder()
                .name(req.getName().trim())
                .description(req.getDescription() != null ? req.getDescription().trim() : null)
                .organizedBy(req.getOrganizedBy().trim())
                .registrationStart(req.getRegistrationStart())
                .registrationEnd(req.getRegistrationEnd())
                .maximumTeamSize(req.getMaximumTeamSize())
                .prizeMoney(req.getPrizeMoney() != null ? req.getPrizeMoney() : 0.0)
                .mode(req.getMode())
                .venue(req.getMode() == HackathonMode.OFFLINE ? req.getVenue().trim() : (req.getVenue() != null ? req.getVenue().trim() : null))
                .registrationLink(req.getRegistrationLink().trim())
                .createdByAdminName(adminName)
                .deleted(false)
                .build();

        Hackathon saved = hackathonRepository.save(hackathon);

        auditLogRepository.save(AuditLog.builder()
                .action("HACKATHON_CREATED")
                .performedBy(admin != null ? admin.getEmail() : "Admin")
                .details("Created hackathon: " + saved.getName() + " (" + saved.getOrganizedBy() + ")")
                .build());

        return mapToResponse(saved, null);
    }

    @Transactional
    public HackathonResponse updateHackathon(Long id, HackathonCreateRequest req) {
        validateHackathonRequest(req);

        Hackathon hackathon = hackathonRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hackathon not found with ID: " + id));

        hackathon.setName(req.getName().trim());
        hackathon.setDescription(req.getDescription() != null ? req.getDescription().trim() : null);
        hackathon.setOrganizedBy(req.getOrganizedBy().trim());
        hackathon.setRegistrationStart(req.getRegistrationStart());
        hackathon.setRegistrationEnd(req.getRegistrationEnd());
        hackathon.setMaximumTeamSize(req.getMaximumTeamSize());
        hackathon.setPrizeMoney(req.getPrizeMoney() != null ? req.getPrizeMoney() : 0.0);
        hackathon.setMode(req.getMode());
        hackathon.setVenue(req.getMode() == HackathonMode.OFFLINE ? req.getVenue().trim() : (req.getVenue() != null ? req.getVenue().trim() : null));
        hackathon.setRegistrationLink(req.getRegistrationLink().trim());

        Hackathon updated = hackathonRepository.save(hackathon);

        CustomUserDetails admin = authService.getCurrentUser();
        auditLogRepository.save(AuditLog.builder()
                .action("HACKATHON_UPDATED")
                .performedBy(admin != null ? admin.getEmail() : "Admin")
                .details("Updated hackathon: " + updated.getName())
                .build());

        return mapToResponse(updated, null);
    }

    @Transactional
    public void deleteHackathon(Long id) {
        Hackathon hackathon = hackathonRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hackathon not found with ID: " + id));

        hackathon.setDeleted(true);
        hackathonRepository.save(hackathon);

        CustomUserDetails admin = authService.getCurrentUser();
        auditLogRepository.save(AuditLog.builder()
                .action("HACKATHON_DELETED")
                .performedBy(admin != null ? admin.getEmail() : "Admin")
                .details("Soft deleted hackathon: " + hackathon.getName())
                .build());
    }

    public List<HackathonResponse> getAllHackathons(Long currentStudentId) {
        return getAllHackathons(currentStudentId, currentStudentId != null);
    }

    public List<HackathonResponse> getAllHackathons(Long currentStudentId, boolean isStudent) {
        LocalDate today = LocalDate.now();
        return hackathonRepository.findByDeletedFalseOrderByRegistrationStartDesc().stream()
                .filter(h -> {
                    if (isStudent) {
                        return h.getRegistrationEnd() != null && !today.isAfter(h.getRegistrationEnd())
                                && h.getComputedStatus() != HackathonStatus.COMPLETED
                                && h.getComputedStatus() != HackathonStatus.REGISTRATION_CLOSED;
                    }
                    return true;
                })
                .map(h -> mapToResponse(h, currentStudentId))
                .collect(Collectors.toList());
    }

    public HackathonResponse getHackathonById(Long id, Long currentStudentId) {
        Hackathon hackathon = hackathonRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hackathon not found with ID: " + id));
        return mapToResponse(hackathon, currentStudentId);
    }

    public List<HackathonResponse> searchHackathons(String keyword, Long currentStudentId) {
        return searchHackathons(keyword, currentStudentId, currentStudentId != null);
    }

    public List<HackathonResponse> searchHackathons(String keyword, Long currentStudentId, boolean isStudent) {
        LocalDate today = LocalDate.now();
        return hackathonRepository.searchHackathons(keyword).stream()
                .filter(h -> {
                    if (isStudent) {
                        return h.getRegistrationEnd() != null && !today.isAfter(h.getRegistrationEnd())
                                && h.getComputedStatus() != HackathonStatus.COMPLETED
                                && h.getComputedStatus() != HackathonStatus.REGISTRATION_CLOSED;
                    }
                    return true;
                })
                .map(h -> mapToResponse(h, currentStudentId))
                .collect(Collectors.toList());
    }

    private void validateHackathonRequest(HackathonCreateRequest req) {
        if (req.getRegistrationEnd().isBefore(req.getRegistrationStart())) {
            throw new BadRequestException("Registration end date cannot be before registration start date.");
        }
        if (req.getMode() == HackathonMode.OFFLINE && (req.getVenue() == null || req.getVenue().trim().isBlank())) {
            throw new BadRequestException("Venue is required for offline hackathons.");
        }
        if (req.getMaximumTeamSize() <= 0) {
            throw new BadRequestException("Maximum team size must be greater than zero.");
        }
        if (req.getPrizeMoney() != null && req.getPrizeMoney() < 0) {
            throw new BadRequestException("Prize money cannot be negative.");
        }
    }

    private HackathonResponse mapToResponse(Hackathon h, Long currentStudentId) {
        long totalTeams = teamRepository.countByHackathonIdAndDeletedFalse(h.getId());
        long totalParticipants = teamMemberRepository.countDistinctParticipantsByHackathonId(h.getId());

        boolean alreadyRegistered = false;
        Long studentTeamId = null;

        if (currentStudentId != null) {
            Optional<Team> existing = teamRepository.findTeamByStudentAndHackathon(currentStudentId, h.getId());
            if (existing.isPresent()) {
                alreadyRegistered = true;
                studentTeamId = existing.get().getId();
            }
        }

        return HackathonResponse.builder()
                .id(h.getId())
                .name(h.getName())
                .description(h.getDescription())
                .organizedBy(h.getOrganizedBy())
                .registrationStart(h.getRegistrationStart())
                .registrationEnd(h.getRegistrationEnd())
                .maximumTeamSize(h.getMaximumTeamSize())
                .prizeMoney(h.getPrizeMoney())
                .mode(h.getMode())
                .venue(h.getVenue())
                .registrationLink(h.getRegistrationLink())
                .status(h.getComputedStatus())
                .createdByAdminName(h.getCreatedByAdminName())
                .totalTeams(totalTeams)
                .totalParticipants(totalParticipants)
                .isRegistrationOpen(h.isRegistrationOpen())
                .alreadyRegistered(alreadyRegistered)
                .studentTeamId(studentTeamId)
                .createdAt(h.getCreatedAt())
                .build();
    }
}

package com.classhack.service;

import com.classhack.entity.Hackathon;
import com.classhack.entity.Team;
import com.classhack.entity.TeamMember;
import com.classhack.exception.ResourceNotFoundException;
import com.classhack.repository.HackathonRepository;
import com.classhack.repository.TeamMemberRepository;
import com.classhack.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final HackathonRepository hackathonRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public void exportHackathonParticipationCsv(Long hackathonId, PrintWriter writer) {
        Hackathon hackathon = hackathonRepository.findByIdAndDeletedFalse(hackathonId)
                .orElseThrow(() -> new ResourceNotFoundException("Hackathon not found with ID: " + hackathonId));

        List<Team> teams = teamRepository.findByHackathonIdAndDeletedFalseOrderByCreatedAtDesc(hackathonId);

        // Header
        writer.println("Hackathon,Team Name,Team Status,Student ID,Student Name,Department,Section,Year,Team Role");

        for (Team team : teams) {
            List<TeamMember> members = teamMemberRepository.findByTeamId(team.getId());
            for (TeamMember m : members) {
                writer.print(escapeCsv(hackathon.getName()));
                writer.print(",");
                writer.print(escapeCsv(team.getTeamName()));
                writer.print(",");
                writer.print(escapeCsv(team.getStatus().name()));
                writer.print(",");
                writer.print(escapeCsv(m.getStudent().getStudentId()));
                writer.print(",");
                writer.print(escapeCsv(m.getStudent().getFullName()));
                writer.print(",");
                writer.print(escapeCsv(m.getStudent().getDepartment()));
                writer.print(",");
                writer.print(escapeCsv(m.getStudent().getSection()));
                writer.print(",");
                writer.print(escapeCsv(m.getStudent().getYear()));
                writer.print(",");
                writer.println(escapeCsv(m.getRole()));
            }
        }
        writer.flush();
    }

    public void exportAllHackathonsSummaryCsv(PrintWriter writer) {
        List<Hackathon> hackathons = hackathonRepository.findByDeletedFalseOrderByRegistrationStartDesc();

        writer.println("Hackathon Name,Organized By,Mode,Venue,Start Date,End Date,Status,Total Teams,Total Participants");

        for (Hackathon h : hackathons) {
            long totalTeams = teamRepository.countByHackathonIdAndDeletedFalse(h.getId());
            long totalParticipants = teamMemberRepository.countDistinctParticipantsByHackathonId(h.getId());

            writer.print(escapeCsv(h.getName()));
            writer.print(",");
            writer.print(escapeCsv(h.getOrganizedBy()));
            writer.print(",");
            writer.print(escapeCsv(h.getMode().name()));
            writer.print(",");
            writer.print(escapeCsv(h.getVenue() != null ? h.getVenue() : "N/A"));
            writer.print(",");
            writer.print(escapeCsv(h.getRegistrationStart().toString()));
            writer.print(",");
            writer.print(escapeCsv(h.getRegistrationEnd().toString()));
            writer.print(",");
            writer.print(escapeCsv(h.getComputedStatus().name()));
            writer.print(",");
            writer.print(totalTeams);
            writer.print(",");
            writer.println(totalParticipants);
        }
        writer.flush();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String result = value;
        if (result.contains(",") || result.contains("\"") || result.contains("\n") || result.contains("\r")) {
            result = result.replace("\"", "\"\"");
            result = "\"" + result + "\"";
        }
        return result;
    }
}

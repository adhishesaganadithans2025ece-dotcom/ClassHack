package com.classhack.controller;

import com.classhack.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/hackathons/{hackathonId}/export-csv")
    public void exportHackathonCsv(@PathVariable Long hackathonId, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"classhack_hackathon_" + hackathonId + "_participation.csv\"");
        reportService.exportHackathonParticipationCsv(hackathonId, response.getWriter());
    }

    @GetMapping("/hackathons/summary-csv")
    public void exportSummaryCsv(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"classhack_hackathons_summary.csv\"");
        reportService.exportAllHackathonsSummaryCsv(response.getWriter());
    }
}

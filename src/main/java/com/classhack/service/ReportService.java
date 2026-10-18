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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final HackathonRepository hackathonRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public String getHackathonName(Long hackathonId) {
        return hackathonRepository.findByIdAndDeletedFalse(hackathonId)
                .map(Hackathon::getName)
                .orElse("Hackathon_" + hackathonId);
    }

    public byte[] exportHackathonRegistrationsExcel(Long hackathonId) throws IOException {
        Hackathon hackathon = hackathonRepository.findByIdAndDeletedFalse(hackathonId)
                .orElseThrow(() -> new ResourceNotFoundException("Hackathon not found with ID: " + hackathonId));

        // Registrations ONLY for the selected hackathon
        List<Team> teams = teamRepository.findByHackathonIdAndDeletedFalseOrderByCreatedAtDesc(hackathonId);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Registrations");
            sheet.setDisplayGridlines(true);

            byte[] navyHeader = new byte[]{(byte) 30, (byte) 58, (byte) 138}; // #1E3A8A
            byte[] borderGray = new byte[]{(byte) 209, (byte) 213, (byte) 219}; // #D1D5DB

            // Styles
            XSSFCellStyle titleStyle = workbook.createCellStyle();
            XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            titleFont.setColor(new XSSFColor(navyHeader, null));
            titleStyle.setFont(titleFont);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle metaStyle = workbook.createCellStyle();
            XSSFFont metaFont = workbook.createFont();
            metaFont.setFontHeightInPoints((short) 9);
            metaFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            metaStyle.setFont(metaFont);
            metaStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 10);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(navyHeader, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setCellBorders(headerStyle);

            XSSFCellStyle dataStyle = workbook.createCellStyle();
            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontHeightInPoints((short) 10);
            dataStyle.setFont(dataFont);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setCellBorders(dataStyle);

            XSSFCellStyle centerDataStyle = workbook.createCellStyle();
            centerDataStyle.cloneStyleFrom(dataStyle);
            centerDataStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle codeStyle = workbook.createCellStyle();
            codeStyle.cloneStyleFrom(dataStyle);
            XSSFFont codeFont = workbook.createFont();
            codeFont.setFontName("Consolas");
            codeFont.setBold(true);
            codeFont.setFontHeightInPoints((short) 10);
            codeStyle.setFont(codeFont);
            codeStyle.setAlignment(HorizontalAlignment.CENTER);

            XSSFCellStyle statusStyle = workbook.createCellStyle();
            statusStyle.cloneStyleFrom(dataStyle);
            XSSFFont statusFont = workbook.createFont();
            statusFont.setBold(true);
            statusFont.setFontHeightInPoints((short) 10);
            statusStyle.setFont(statusFont);
            statusStyle.setAlignment(HorizontalAlignment.CENTER);

            int rowIdx = 0;

            // Row 0: Title
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.setHeightInPoints(24);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ClassHack — Registrations: " + hackathon.getName());
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

            // Row 1: Hackathon Metadata
            Row metaRow = sheet.createRow(rowIdx++);
            metaRow.setHeightInPoints(18);
            Cell metaCell = metaRow.createCell(0);
            metaCell.setCellValue(String.format("Hackathon ID: %d | Mode: %s | Venue: %s | Organized By: %s | Registration: %s to %s | Status: %s",
                    hackathon.getId(),
                    hackathon.getMode() != null ? hackathon.getMode().name() : "N/A",
                    hackathon.getVenue() != null ? hackathon.getVenue() : "N/A",
                    hackathon.getOrganizedBy() != null ? hackathon.getOrganizedBy() : "N/A",
                    hackathon.getRegistrationStart() != null ? hackathon.getRegistrationStart().toString() : "N/A",
                    hackathon.getRegistrationEnd() != null ? hackathon.getRegistrationEnd().toString() : "N/A",
                    hackathon.getComputedStatus() != null ? hackathon.getComputedStatus().name() : "N/A"
            ));
            metaCell.setCellStyle(metaStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 11));

            // Row 2: Blank separator
            sheet.createRow(rowIdx++).setHeightInPoints(8);

            // Row 3: Headers
            String[] headers = {
                    "Hackathon Name",
                    "Hackathon ID",
                    "Registration ID",
                    "Student Name",
                    "Student ID / Roll Number",
                    "Department",
                    "Section",
                    "Year",
                    "Student Email",
                    "Team Name",
                    "Team Members",
                    "Registration Date",
                    "Registration Status"
            };

            int headerRowIdx = rowIdx++;
            Row headerRow = sheet.createRow(headerRowIdx);
            headerRow.setHeightInPoints(26);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            // Data Rows
            int totalParticipants = 0;
            for (Team team : teams) {
                List<TeamMember> members = teamMemberRepository.findByTeamId(team.getId());
                String teamRoster = members.stream()
                        .map(m -> m.getStudent().getFullName() + " (" + m.getRole() + ")")
                        .collect(Collectors.joining(", "));

                String regDate = "N/A";
                if (team.getCreatedAt() != null) {
                    regDate = team.getCreatedAt().atZone(ZoneOffset.UTC)
                            .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                }

                for (TeamMember m : members) {
                    totalParticipants++;
                    Row r = sheet.createRow(rowIdx++);
                    r.setHeightInPoints(20);

                    // 0: Hackathon Name
                    Cell c0 = r.createCell(0);
                    c0.setCellValue(hackathon.getName());
                    c0.setCellStyle(dataStyle);

                    // 1: Hackathon ID
                    Cell c1 = r.createCell(1);
                    c1.setCellValue(hackathon.getId());
                    c1.setCellStyle(centerDataStyle);

                    // 2: Registration ID
                    Cell c2 = r.createCell(2);
                    c2.setCellValue("REG-" + team.getId() + "-" + m.getId());
                    c2.setCellStyle(codeStyle);

                    // 3: Student Name
                    Cell c3 = r.createCell(3);
                    c3.setCellValue(m.getStudent().getFullName());
                    c3.setCellStyle(dataStyle);

                    // 4: Student ID / Roll Number
                    Cell c4 = r.createCell(4);
                    c4.setCellValue(m.getStudent().getRollNumber());
                    c4.setCellStyle(codeStyle);

                    // 5: Department
                    Cell c5 = r.createCell(5);
                    c5.setCellValue(m.getStudent().getDepartment());
                    c5.setCellStyle(centerDataStyle);

                    // 6: Section
                    Cell c6 = r.createCell(6);
                    c6.setCellValue(m.getStudent().getSection());
                    c6.setCellStyle(centerDataStyle);

                    // 7: Year
                    Cell c7 = r.createCell(7);
                    c7.setCellValue(m.getStudent().getYear());
                    c7.setCellStyle(centerDataStyle);

                    // 8: Student Email
                    Cell c8 = r.createCell(8);
                    c8.setCellValue(m.getStudent().getEmail());
                    c8.setCellStyle(dataStyle);

                    // 9: Team Name
                    Cell c9 = r.createCell(9);
                    c9.setCellValue(team.getTeamName());
                    c9.setCellStyle(dataStyle);

                    // 10: Team Members
                    Cell c10 = r.createCell(10);
                    c10.setCellValue(teamRoster);
                    c10.setCellStyle(dataStyle);

                    // 11: Registration Date
                    Cell c11 = r.createCell(11);
                    c11.setCellValue(regDate);
                    c11.setCellStyle(centerDataStyle);

                    // 12: Registration Status
                    Cell c12 = r.createCell(12);
                    c12.setCellValue(team.getStatus().name());
                    c12.setCellStyle(statusStyle);
                }
            }

            if (totalParticipants == 0) {
                Row emptyRow = sheet.createRow(rowIdx++);
                emptyRow.setHeightInPoints(22);
                Cell c = emptyRow.createCell(0);
                c.setCellValue("No student registrations found for this hackathon.");
                c.setCellStyle(dataStyle);
                sheet.addMergedRegion(new CellRangeAddress(emptyRow.getRowNum(), emptyRow.getRowNum(), 0, headers.length - 1));
            } else {
                sheet.setAutoFilter(new CellRangeAddress(headerRowIdx, rowIdx - 1, 0, headers.length - 1));
            }

            // Column widths
            int[] minWidths = {22, 14, 18, 20, 18, 12, 10, 10, 26, 20, 32, 18, 16};
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                int minWidthIn256 = minWidths[i] * 256;
                if (currentWidth < minWidthIn256) {
                    sheet.setColumnWidth(i, minWidthIn256);
                } else {
                    sheet.setColumnWidth(i, currentWidth + 768);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setCellBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

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

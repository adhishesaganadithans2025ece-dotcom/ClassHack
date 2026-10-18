package com.classhack.service;

import com.classhack.entity.Student;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class StudentCredentialExportService {

    private static final int EXPIRY_MINUTES = 60;

    public record InitialCredential(
            String studentId,
            String plaintextPassword,
            LocalDateTime createdAt,
            String batchId,
            Long adminId
    ) {
        public boolean isExpired() {
            return createdAt.plusMinutes(EXPIRY_MINUTES).isBefore(LocalDateTime.now());
        }
    }

    // In-memory thread-safe storage for temporary initial passwords
    private final ConcurrentHashMap<String, InitialCredential> credentialMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> batchMap = new ConcurrentHashMap<>();

    /**
     * Store initial plaintext password in memory for temporary authorized Excel download.
     * Note: Plaintext passwords are NEVER saved to the database, disk, or logs.
     */
    public void recordInitialCredential(String studentId, String plaintextPassword, String batchId, Long adminId) {
        if (studentId == null || plaintextPassword == null) {
            return;
        }
        purgeExpired();
        String normalizedId = studentId.trim().toUpperCase();
        InitialCredential credential = new InitialCredential(
                normalizedId,
                plaintextPassword,
                LocalDateTime.now(),
                batchId,
                adminId
        );
        credentialMap.put(normalizedId, credential);

        if (batchId != null && !batchId.isBlank()) {
            batchMap.computeIfAbsent(batchId.trim(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(normalizedId);
        }
    }

    /**
     * Retrieve the initial password from in-memory cache if available and not expired.
     */
    public Optional<String> getInitialPassword(String studentId) {
        if (studentId == null) {
            return Optional.empty();
        }
        purgeExpired();
        InitialCredential credential = credentialMap.get(studentId.trim().toUpperCase());
        if (credential != null && !credential.isExpired()) {
            return Optional.of(credential.plaintextPassword());
        }
        return Optional.empty();
    }

    /**
     * Retrieve student IDs belonging to a creation batch.
     */
    public List<String> getStudentIdsForBatch(String batchId) {
        if (batchId == null) {
            return Collections.emptyList();
        }
        purgeExpired();
        List<String> ids = batchMap.get(batchId.trim());
        return ids != null ? new ArrayList<>(ids) : Collections.emptyList();
    }

    /**
     * Evict credentials older than EXPIRY_MINUTES.
     */
    public void purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(EXPIRY_MINUTES);
        credentialMap.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    /**
     * Clear specific batch from cache.
     */
    public void clearBatch(String batchId) {
        if (batchId == null) return;
        List<String> ids = batchMap.remove(batchId.trim());
        if (ids != null) {
            for (String id : ids) {
                credentialMap.remove(id);
            }
        }
    }

    /**
     * Generate an Excel (.xlsx) workbook in-memory containing student credentials.
     * Columns:
     * - Student Name
     * - Student ID / Roll Number
     * - Department
     * - Section
     * - Year
     * - Email Address
     * - Username
     * - Initial Password
     * - Password Change Required
     */
    public byte[] generateStudentCredentialsExcel(List<Student> students, String subtitleInfo) throws IOException {
        purgeExpired();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Student Credentials");
            sheet.setDisplayGridlines(true);

            // Palette colors
            byte[] primaryBlue = new byte[]{(byte) 30, (byte) 58, (byte) 138}; // #1E3A8A
            byte[] bannerBg = new byte[]{(byte) 239, (byte) 246, (byte) 255};    // #EFF6FF
            byte[] warningBg = new byte[]{(byte) 254, (byte) 242, (byte) 242};   // #FEF2F2
            byte[] borderGray = new byte[]{(byte) 209, (byte) 213, (byte) 219};  // #D1D5DB

            // Styles
            // Title style
            XSSFCellStyle titleStyle = workbook.createCellStyle();
            XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(new XSSFColor(primaryBlue, null));
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Warning Banner Style
            XSSFCellStyle warningStyle = workbook.createCellStyle();
            XSSFFont warningFont = workbook.createFont();
            warningFont.setBold(true);
            warningFont.setColor(IndexedColors.RED.getIndex());
            warningFont.setFontHeightInPoints((short) 9);
            warningStyle.setFont(warningFont);
            warningStyle.setFillForegroundColor(new XSSFColor(warningBg, null));
            warningStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            warningStyle.setAlignment(HorizontalAlignment.LEFT);
            warningStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Header Style
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 10);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(primaryBlue, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(headerStyle, BorderStyle.THIN);

            // Regular Data Cell Style
            XSSFCellStyle dataStyle = workbook.createCellStyle();
            XSSFFont dataFont = workbook.createFont();
            dataFont.setFontHeightInPoints((short) 10);
            dataStyle.setFont(dataFont);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(dataStyle, BorderStyle.THIN);

            // Centered Data Style
            XSSFCellStyle centerDataStyle = workbook.createCellStyle();
            centerDataStyle.cloneStyleFrom(dataStyle);
            centerDataStyle.setAlignment(HorizontalAlignment.CENTER);

            // Monospace / Code Style for IDs and Passwords
            XSSFCellStyle codeStyle = workbook.createCellStyle();
            codeStyle.cloneStyleFrom(dataStyle);
            XSSFFont codeFont = workbook.createFont();
            codeFont.setFontName("Consolas");
            codeFont.setBold(true);
            codeFont.setFontHeightInPoints((short) 10);
            codeStyle.setFont(codeFont);
            codeStyle.setAlignment(HorizontalAlignment.CENTER);

            // Password Reset Required Style (for older students where initial password is unavailable)
            XSSFCellStyle unavailStyle = workbook.createCellStyle();
            unavailStyle.cloneStyleFrom(dataStyle);
            XSSFFont unavailFont = workbook.createFont();
            unavailFont.setItalic(true);
            unavailFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            unavailStyle.setFont(unavailFont);
            unavailStyle.setAlignment(HorizontalAlignment.CENTER);

            int rowIdx = 0;

            // Row 0: Title
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.setHeightInPoints(24);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ClassHack — Student Initial Login Credentials");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

            // Row 1: Subtitle / Scope & Export Metadata
            Row metaRow = sheet.createRow(rowIdx++);
            metaRow.setHeightInPoints(18);
            Cell metaCell = metaRow.createCell(0);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String scopeText = (subtitleInfo != null && !subtitleInfo.isBlank()) ? subtitleInfo + " | " : "";
            metaCell.setCellValue(scopeText + "Exported on: " + timestamp + " | Total Records: " + students.size());
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 8));

            // Row 2: Security Warning Banner
            Row warnRow = sheet.createRow(rowIdx++);
            warnRow.setHeightInPoints(20);
            Cell warnCell = warnRow.createCell(0);
            warnCell.setCellValue("CONFIDENTIAL: Contains sensitive initial login passwords. Share securely with students and delete this local copy after distribution.");
            warnCell.setCellStyle(warningStyle);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 8));

            // Row 3: Blank separator
            sheet.createRow(rowIdx++).setHeightInPoints(8);

            // Row 4: Column Headers
            String[] headers = {
                    "Student Name",
                    "Student ID / Roll Number",
                    "Department",
                    "Section",
                    "Year",
                    "Email Address",
                    "Username",
                    "Initial Password",
                    "Password Change Required"
            };

            int headerRowIdx = rowIdx++;
            Row headerRow = sheet.createRow(headerRowIdx);
            headerRow.setHeightInPoints(26);

            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // Data Rows
            if (students.isEmpty()) {
                Row emptyRow = sheet.createRow(rowIdx++);
                emptyRow.setHeightInPoints(22);
                Cell c = emptyRow.createCell(0);
                c.setCellValue("No student records available for the selected export criteria.");
                c.setCellStyle(dataStyle);
                sheet.addMergedRegion(new CellRangeAddress(emptyRow.getRowNum(), emptyRow.getRowNum(), 0, 8));
            } else {
                for (Student s : students) {
                    Row r = sheet.createRow(rowIdx++);
                    r.setHeightInPoints(20);

                    // 1. Student Name
                    Cell c0 = r.createCell(0);
                    c0.setCellValue(s.getFullName() != null ? s.getFullName() : "");
                    c0.setCellStyle(dataStyle);

                    // 2. Student ID / Roll Number
                    Cell c1 = r.createCell(1);
                    c1.setCellValue(s.getRollNumber() != null ? s.getRollNumber() : s.getStudentId());
                    c1.setCellStyle(codeStyle);

                    // 3. Department
                    Cell c2 = r.createCell(2);
                    c2.setCellValue(s.getDepartment() != null ? s.getDepartment() : "");
                    c2.setCellStyle(centerDataStyle);

                    // 4. Section
                    Cell c3 = r.createCell(3);
                    c3.setCellValue(s.getSection() != null ? s.getSection() : "");
                    c3.setCellStyle(centerDataStyle);

                    // 5. Year
                    Cell c4 = r.createCell(4);
                    c4.setCellValue(s.getYear() != null ? s.getYear() : "");
                    c4.setCellStyle(centerDataStyle);

                    // 6. Email Address
                    Cell c5 = r.createCell(5);
                    c5.setCellValue(s.getEmail() != null ? s.getEmail() : "");
                    c5.setCellStyle(dataStyle);

                    // 7. Username (Student ID or Email)
                    Cell c6 = r.createCell(6);
                    c6.setCellValue(s.getStudentId() != null ? s.getStudentId() : s.getEmail());
                    c6.setCellStyle(codeStyle);

                    // 8. Initial Password
                    Cell c7 = r.createCell(7);
                    Optional<String> initialPwd = getInitialPassword(s.getStudentId());
                    if (initialPwd.isPresent()) {
                        c7.setCellValue(initialPwd.get());
                        c7.setCellStyle(codeStyle);
                    } else {
                        // Older student created before this feature or expired from memory cache
                        c7.setCellValue("Password reset required");
                        c7.setCellStyle(unavailStyle);
                    }

                    // 9. Password Change Required
                    Cell c8 = r.createCell(8);
                    c8.setCellValue(s.isPasswordChangeRequired() ? "YES" : "NO");
                    c8.setCellStyle(centerDataStyle);
                }
            }

            // Auto-filter on data columns
            if (!students.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(headerRowIdx, rowIdx - 1, 0, headers.length - 1));
            }

            // Column widths
            int[] minWidths = {22, 18, 12, 10, 10, 28, 18, 22, 16};
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                int minWidthIn256 = minWidths[i] * 256;
                if (currentWidth < minWidthIn256) {
                    sheet.setColumnWidth(i, minWidthIn256);
                } else {
                    sheet.setColumnWidth(i, currentWidth + 768); // add padding
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setBorders(CellStyle style, BorderStyle border) {
        style.setBorderTop(border);
        style.setBorderBottom(border);
        style.setBorderLeft(border);
        style.setBorderRight(border);
    }
}

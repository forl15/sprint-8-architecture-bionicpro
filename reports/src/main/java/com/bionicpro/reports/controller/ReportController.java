package com.bionicpro.reports.controller;

import com.bionicpro.reports.model.ReportRow;
import com.bionicpro.reports.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private static final String[] CSV_HEADER = {
            "report_date", "user_id", "prosthesis_id", "customer_name", "customer_email",
            "total_events", "successful_movements", "failed_movements",
            "avg_response_ms", "max_response_ms", "battery_avg_pct", "battery_min_pct", "updated_at"
    };

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<byte[]> getReports(
            @RequestParam(name = "reportDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            SecurityContext securityContext) {

        String userId = extractUserId(securityContext);
        if (userId == null || userId.isBlank()) {
            log.warn("GET /reports rejected: no user_id (preferred_username) in token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new byte[0]);
        }

        List<ReportRow> rows = reportService.findReport(userId, reportDate);
        LocalDate resolvedDate = rows.isEmpty()
                ? (reportDate != null ? reportDate : LocalDate.now())
                : rows.get(0).reportDate();
        String csv = toCsv(rows);
        String filename = String.format("prosthesis_report_%s_%s.csv", userId, resolvedDate);

        log.info("GET /reports user_id={} reportDate={} -> 200 CSV, rows={}", userId, resolvedDate, rows.size());
        log.info("GET /reports response body: {}", csv.replace("\r\n", " | "));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(
                org.springframework.http.ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(csv.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }

    private String extractUserId(SecurityContext securityContext) {
        if (securityContext.getAuthentication() != null
                && securityContext.getAuthentication().getCredentials() instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("preferred_username");
            log.info("GET /reports token: sub={}, preferred_username={}, email={}, realm_access={}",
                    jwt.getSubject(), userId, jwt.getClaimAsString("email"), jwt.getClaim("realm_access"));
            return userId;
        }
        return null;
    }

    private String toCsv(List<ReportRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", CSV_HEADER)).append("\r\n");
        for (ReportRow r : rows) {
            sb.append(String.join(",",
                    csv(r.reportDate() == null ? "" : r.reportDate().toString()),
                    csv(r.userId()),
                    csv(r.prosthesisId()),
                    csv(r.customerName()),
                    csv(r.customerEmail()),
                    Long.toString(r.totalEvents()),
                    Long.toString(r.successfulMovements()),
                    Long.toString(r.failedMovements()),
                    str(r.avgResponseMs()),
                    str(r.maxResponseMs()),
                    r.batteryAvgPct() == null ? "" : str(r.batteryAvgPct()),
                    r.batteryMinPct() == null ? "" : str(r.batteryMinPct()),
                    r.updatedAt() == null ? "" : r.updatedAt().toString()))
              .append("\r\n");
        }
        return sb.toString();
    }

    private static String str(double v) {
        return String.valueOf(v);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}

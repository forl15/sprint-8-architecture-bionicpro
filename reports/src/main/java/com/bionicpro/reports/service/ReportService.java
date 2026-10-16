package com.bionicpro.reports.service;

import com.bionicpro.reports.model.ReportRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReportService {

    private static final String COLUMNS = String.join(", ",
            "report_date", "user_id", "prosthesis_id", "customer_name", "customer_email",
            "total_events", "successful_movements", "failed_movements",
            "avg_response_ms", "max_response_ms", "battery_avg_pct", "battery_min_pct", "updated_at");

    private static final RowMapper<ReportRow> ROW_MAPPER = (rs, rowNum) -> {
        java.sql.Date date = rs.getDate("report_date");
        java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
        float batteryAvg = rs.getFloat("battery_avg_pct");
        float batteryMin = rs.getFloat("battery_min_pct");
        return new ReportRow(
                date == null ? null : date.toLocalDate(),
                rs.getString("user_id"),
                rs.getString("prosthesis_id"),
                rs.getString("customer_name"),
                rs.getString("customer_email"),
                rs.getLong("total_events"),
                rs.getLong("successful_movements"),
                rs.getLong("failed_movements"),
                rs.getDouble("avg_response_ms"),
                rs.getDouble("max_response_ms"),
                rs.wasNull() ? null : (double) batteryAvg,
                rs.wasNull() ? null : (double) batteryMin,
                updatedAt == null ? null : updatedAt.toLocalDateTime()
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public ReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReportRow> findReport(String userId, LocalDate reportDate) {
        LocalDate target = reportDate != null ? reportDate : findLatestReportDate(userId);
        if (target == null) {
            return List.of();
        }
        String sql = "SELECT " + COLUMNS + " FROM prosthesis_daily_report"
                + " WHERE user_id = ? AND report_date = ? ORDER BY prosthesis_id";
        return jdbcTemplate.query(sql, ROW_MAPPER, userId, target);
    }

    private LocalDate findLatestReportDate(String userId) {
        List<LocalDate> result = jdbcTemplate.query(
                "SELECT max(report_date) FROM prosthesis_daily_report WHERE user_id = ?",
                (rs, rowNum) -> {
                    java.sql.Date d = rs.getDate(1);
                    return d == null ? null : d.toLocalDate();
                },
                userId);
        return (result.isEmpty() || result.get(0) == null) ? null : result.get(0);
    }
}

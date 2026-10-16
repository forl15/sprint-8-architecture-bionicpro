package com.bionicpro.reports.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReportRow(
        LocalDate reportDate,
        String userId,
        String prosthesisId,
        String customerName,
        String customerEmail,
        long totalEvents,
        long successfulMovements,
        long failedMovements,
        double avgResponseMs,
        double maxResponseMs,
        Double batteryAvgPct,
        Double batteryMinPct,
        LocalDateTime updatedAt
) {
}

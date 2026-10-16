package com.bionicpro.reports.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseInitializer {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS prosthesis_daily_report
            (
                report_date Date,
                user_id String,
                prosthesis_id String,
                customer_name String,
                customer_email String,
                total_events UInt64,
                successful_movements UInt64,
                failed_movements UInt64,
                avg_response_ms Float32,
                max_response_ms Float32,
                battery_avg_pct Nullable(Float32),
                battery_min_pct Nullable(Float32),
                updated_at DateTime DEFAULT now()
            )
            ENGINE = MergeTree()
            PARTITION BY toYYYYMM(report_date)
            ORDER BY (user_id, prosthesis_id, report_date)
            """;

    private static final String SEED_DATA_SQL = """
            INSERT INTO prosthesis_daily_report
                (report_date, user_id, prosthesis_id, customer_name, customer_email,
                 total_events, successful_movements, failed_movements,
                 avg_response_ms, max_response_ms, battery_avg_pct, battery_min_pct, updated_at)
            VALUES
                (today(), 'user1', 'prosthesis-501', 'Иван Петров', 'ivan.petrov@example.com',
                 80, 75, 5, 63.14, 178.42, 90.25, 47.18, now()),
                (today(), 'user2', 'prosthesis-502', 'Мария Смирнова', 'maria.smirnova@example.com',
                 80, 77, 3, 58.92, 165.30, 92.40, 51.35, now()),
                (today(), 'user3', 'prosthesis-503', 'Мария Смирнова', 'maria.smirnova@example.com',
                 80, 74, 6, 67.55, 181.20, 88.75, 45.90, now()),
                (today(), 'user4', 'prosthesis-504', 'Алексей Иванов', 'alexey.ivanov@example.com',
                 80, 76, 4, 61.08, 172.65, 91.10, 49.52, now())
            """;

    @Bean
    public ApplicationRunner initReportTable(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute(CREATE_TABLE_SQL);
            Integer count = jdbcTemplate.queryForObject("SELECT count() FROM prosthesis_daily_report", Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute(SEED_DATA_SQL);
            }
        };
    }
}

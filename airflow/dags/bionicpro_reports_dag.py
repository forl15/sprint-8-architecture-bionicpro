import random
from collections import defaultdict
from datetime import date, datetime

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.common.sql.operators.sql import SQLExecuteQueryOperator
from airflow.utils.dates import days_ago

TABLE_NAME = "prosthesis_daily_report"


def generate_source_data(**context):
    report_date = context["logical_date"].date()
    rng = random.Random(report_date.isoformat())

    crm = [
        {"user_id": "user1", "customer_name": "Иван Петров",
         "customer_email": "ivan.petrov@example.com",
         "prosthesis_ids": ["prosthesis-501"]},
        {"user_id": "user2", "customer_name": "Мария Смирнова",
         "customer_email": "maria.smirnova@example.com",
         "prosthesis_ids": ["prosthesis-502", "prosthesis-503"]},
        {"user_id": "user3", "customer_name": "Алексей Иванов",
         "customer_email": "alexey.ivanov@example.com",
         "prosthesis_ids": ["prosthesis-504"]},
    ]

    telemetry = []
    for customer in crm:
        for prosthesis_id in customer["prosthesis_ids"]:
            for i in range(80):
                success = rng.random() < 0.94
                telemetry.append({
                    "user_id": customer["user_id"],
                    "prosthesis_id": prosthesis_id,
                    "successful": success,
                    "response_ms": round(
                        rng.uniform(35, 95) if success else rng.uniform(100, 180), 2
                    ),
                    "battery_pct": round(
                        max(5, 100 - i * rng.uniform(0.03, 0.12)), 2
                    ),
                })

    # Сохраняем итоговые строки отчёта как SQL-инструкции INSERT
    transformed = transform_data(report_date, crm, telemetry)
    rows = transformed["rows"]
    columns = [
        "report_date", "user_id", "prosthesis_id",
        "customer_name", "customer_email", "total_events",
        "successful_movements", "failed_movements",
        "avg_response_ms", "max_response_ms",
        "battery_avg_pct", "battery_min_pct", "updated_at",
    ]
    with open('./dags/sql/prosthesis_daily_report.sql', 'w') as f:
        for row in rows:
            values = ", ".join(sql_literal(v) for v in row)
            f.write(
                f"INSERT INTO {TABLE_NAME} ({', '.join(columns)}) "
                f"VALUES ({values});\n"
            )
    return f"Сгенерировано строк: {len(rows)}, дата: {report_date}"


def sql_literal(value):
    """Возвращает литерал для подстановки в SQL-запрос."""
    if value is None:
        return "NULL"
    if isinstance(value, (date, datetime)):
        return f"'{value.strftime('%Y-%m-%d %H:%M:%S')}'"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def transform_data(report_date, crm, telemetry):
    """Join телеметрии с CRM и агрегация по пользователю/протезу/дню."""
    crm_by_user = {c["user_id"]: c for c in crm}
    agg = defaultdict(lambda: {
        "total": 0, "success": 0, "failed": 0, "response_sum": 0.0,
        "response_max": 0.0, "battery_sum": 0.0, "battery_count": 0,
        "battery_min": None,
    })

    for event in telemetry:
        key = (event["user_id"], event["prosthesis_id"])
        a = agg[key]
        a["total"] += 1
        a["success" if event["successful"] else "failed"] += 1
        a["response_sum"] += event["response_ms"]
        a["response_max"] = max(a["response_max"], event["response_ms"])
        battery = event["battery_pct"]
        a["battery_sum"] += battery
        a["battery_count"] += 1
        if a["battery_min"] is None or battery < a["battery_min"]:
            a["battery_min"] = battery

    now = datetime.utcnow().replace(microsecond=0)
    rows = []
    for (user_id, prosthesis_id), a in agg.items():
        customer = crm_by_user[user_id]
        rows.append([
            report_date, user_id, prosthesis_id,
            customer["customer_name"], customer["customer_email"],
            a["total"], a["success"], a["failed"],
            round(a["response_sum"] / a["total"], 2),
            round(a["response_max"], 2),
            round(a["battery_sum"] / a["battery_count"], 2)
                if a["battery_count"] else None,
            a["battery_min"], now,
        ])
    return {"report_date": report_date, "rows": rows}


# Определяем DAG
with DAG('bionicpro_prosthesis_daily_report',
         start_date=days_ago(1),
         schedule_interval='@daily',
         catchup=False,
         max_active_runs=1) as dag:

    # Создаём таблицу в ClickHouse
    create_table = SQLExecuteQueryOperator(
        task_id='create_table',
        conn_id='write_to_clickhouse',
        split_statements=True,
        sql=f"""
        CREATE TABLE IF NOT EXISTS {TABLE_NAME}
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
        """
    )

    # Генерируем данные, агрегируем и сохраняем SQL-вставки
    generate_queries = PythonOperator(
        task_id='generate_source_data',
        python_callable=generate_source_data,
    )

    # Запускаем выполнение SQL-файла вставки данных в ClickHouse
    run_insert_queries = SQLExecuteQueryOperator(
        task_id='run_insert_queries',
        conn_id='write_to_clickhouse',
        split_statements=True,
        sql='sql/prosthesis_daily_report.sql'
    )

    create_table >> generate_queries >> run_insert_queries
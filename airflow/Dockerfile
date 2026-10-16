FROM apache/airflow:2.11.2-python3.11
USER root

# Install Python dependencies
COPY requirements.txt /requirements.txt
RUN pip3 install --upgrade pip
RUN pip3 install --no-cache-dir -r /requirements.txt

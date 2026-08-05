# DataPulse ⚡

> **Real-Time Data Quality & Observability Platform**  
> *Know Your Data Before Your Business Depends on It.*

DataPulse is a real-time data quality and observability platform. It monitors streaming data pipelines, runs validation rule checks on incoming events, computes dynamic data quality scores, tracks schema evolution, detects statistical anomalies (outliers & drift), and displays observations on a premium dark-mode dashboard.

---

## 🏗️ System Architecture

```text
                   +----------------------+
                   |  Data Sources        |
                   | CSV | API | IoT | DB |
                   +----------+-----------+
                              |
                              v
                +--------------------------+
                | Java Ingestion Service   |
                | Spring Boot              |
                +-----------+--------------+
                            |
                            v
                    Apache Kafka Topics
                            |
            +---------------+----------------+
            |                                |
            v                                v
   Spark Structured Streaming      Dead Letter Queue (DLQ)
            |
            +-------------------------------+
            | Validation                    |
            | Schema Evolution              |
            | Duplicate Detection           |
            | Drift Detection               |
            | Outlier Detection             |
            +---------------+---------------+
                            |
                            v
             Simulated HDFS File System
                            |
                            v
                  PostgreSQL (Metadata)
                            |
                            v
             Java Analytics & Rule Service (REST/WS)
                            |
                            v
                  React Monitoring Dashboard
```

### Key Components

1. **Data Ingestion Service (Spring Boot)**: Receives raw single events, batch JSON arrays, or multi-line CSVs. Registers ingestion job metrics in the database and streams events to Apache Kafka.
2. **Streaming pipeline (Apache Kafka)**: Ingests thousands of events per second across topics: `sales-data`, `customer-data`, `sensor-data`, `payment-data`, and `inventory-data`.
3. **Spark Structured Streaming Engine**: Subscribes to Kafka brokers in real-time, runs data profiling on micro-batches, evaluates dynamic validation rules fetched from PostgreSQL, calculates statistical outliers (Z-Score) and drift, and commits results to PostgreSQL.
4. **Local Fallback Mode**: When Docker/Kafka is unavailable, the Spring Boot service catches connection errors and seamlessly triggers an in-memory simulation processor. It validates events using identical rules, writes results to H2 database, and broadcasts updates, providing an out-of-the-box run path.
5. **Observability Dashboard (React + ECharts)**: A premium glassmorphism dark-theme dashboard. Displays live charts, throughput rates, dataset health mapping, rules editors, and simulated data generators.

---

## 🛠️ Technology Stack

* **Core Backend**: Java 21, Spring Boot 3.1.5, Spring Security (JWT), Spring Data JPA
* **Streaming & Analytics**: Apache Kafka (KRaft), Apache Spark Structured Streaming 3.5.0
* **Databases & Storage**: PostgreSQL 15, H2 (In-memory fallback), Simulated HDFS (local file paths)
* **Frontend**: React 18, Vite, Apache ECharts, Vanilla CSS
* **Infrastructure**: Docker, Docker Compose

---

## 📁 Repository Structure

```text
datapulse/
├── docker-compose.yml             # PostgreSQL & Kafka KRaft composition
├── init.sql                       # Database schema and initial data seeding
├── README.md                      # Comprehensive guide
├── datapulse-backend/             # Ingestion, APIs, WebSockets & Local Simulation
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/datapulse/backend/
│       │   ├── config/            # JWT, Security, and WebSocket configs
│       │   ├── controller/        # REST controllers (Auth, Rules, Ingest, Alerts)
│       │   ├── model/             # JPA entity tables (User, DataSource, Alert, Metric)
│       │   ├── repository/        # Spring Data JPA repositories
│       │   └── service/           # Ingestion, WebSocket broadcast, and local simulation
│       └── resources/             # application.yml and fallback H2 init SQL scripts
├── datapulse-spark-processor/     # Standalone Apache Spark streaming jar
│   ├── pom.xml
│   └── src/main/java/com/datapulse/spark/
│       └── SparkStreamingApp.java # Spark Structured Streaming foreachBatch runner
└── datapulse-dashboard/           # Vite React SPA using premium CSS
    ├── package.json
    ├── index.html
    └── src/
        ├── index.css              # Custom styled CSS animations & glass variables
        ├── App.jsx                # Coordinates WebSocket stream and active tab views
        └── components/            # Login, Overview, Charts, Rules, Alerts, Ingestion
```

---

## 🗄️ Database Schema

Seed tables:
* `users`: Houses logins (`admin` / password `$2a$10$...` BCrypt hash of `password`).
* `data_sources`: Master table mapping pipelines (`sales-data`, `customer-data`, etc.).
* `validation_rules`: Holds rule thresholds (e.g. REQUIRED_FIELD, INVALID_EMAIL, OUTLIER, DRIFT, CUSTOM_CHECK).
* `quality_metrics`: Computes percentages of missing, duplicate, and invalid fields to yield a `quality_score`.
* `alerts`: Stores flagged warnings, errors, or critical schema breaches.
* `ingestion_jobs`: Logs job durations and completed records counts.

---

## 🔌 Core REST APIs

### 1. Authentication
* `POST /api/auth/login`: Logs in user, returns JWT and user roles.
* `POST /api/auth/register`: Adds user to database.

### 2. Ingestion
* `POST /api/ingest/event?topic={topic}`: Ingest single JSON record.
* `POST /api/ingest/json?topic={topic}`: Ingest bulk JSON array.
* `POST /api/ingest/csv?topic={topic}`: Upload multi-line CSV datasets.

### 3. Rules & Alerts Configuration
* `GET /api/rules`: Returns all rules.
* `PUT /api/rules/{id}`: Modify rule severity thresholds.
* `PATCH /api/rules/{id}/toggle`: Enable or disable rule checks instantly.
* `GET /api/alerts`: List active unresolved quality alerts.
* `PATCH /api/alerts/{id}/resolve`: Acknowledge and resolve an alert.

### 4. Live Stream Simulator
* `POST /api/dashboard/simulator/start`: Toggle on continuous IoT simulator.
* `POST /api/dashboard/simulator/stop`: Deactivate simulator loop.
* `POST /api/dashboard/simulator/anomalies`: Post `{"induceAnomalies": true}` to inject validation errors.

### 5. WebSocket Connection
* Endpoint: `ws://localhost:8080/ws/metrics` (broadcasts live throughput, batch scores, and alerts).

---

## 🚀 Execution & Setup Guide

### Mode A: Lightweight Local Fallback Mode (No Docker/Kafka needed)
This mode runs the entire system using an in-memory H2 database and the built-in Java streaming simulation. It provides immediate functionality on any desktop.

#### 1. Compile and Run the Backend
Go to the backend folder, compile, and run it using the `h2` profile (active by default):
```bash
cd datapulse-backend
mvn clean package
java -jar target/datapulse-backend-1.0.0.jar
```
*Note: The app will start on port `8080` and log: "H2 Database initialization completed."*

#### 2. Run the React Dashboard
Open a new terminal tab and start the frontend Vite developer server:
```bash
cd datapulse-dashboard
npm install
npm run dev
```
Open your browser to `http://localhost:5173`. Login with `admin` / `password`.

---

### Mode B: Full Distributed Mode (Postgres + Kafka + Spark)

#### 1. Launch Docker Infrastructure
Start the PostgreSQL and Kafka KRaft database cluster:
```bash
docker-compose up -d
```

#### 2. Compile and Run the Backend on Dev Profile
Switch the Spring Boot active profile to `dev` by setting environment variables or command-line parameters:
```bash
cd datapulse-backend
mvn clean package
java -jar -Dspring.profiles.active=dev target/datapulse-backend-1.0.0.jar
```

#### 3. Compile and Run the Spark Structured Streaming App
Build the Spark fat-jar and run the streaming pipeline:
```bash
cd datapulse-spark-processor
mvn clean package
# Run Spark locally with Java 17/21 Opens arguments
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED -jar target/datapulse-spark-processor-1.0.0-jar-with-dependencies.jar
```

#### 4. Load the Dashboard
Start the Vite frontend inside `datapulse-dashboard` and open `http://localhost:5173`.

---

## 📊 Testing the Quality Platform

1. **Dashboard Overview**: Click **Start Data Simulator** on the live tab. You will see throughput gauges immediately spike to ~3.3 rec/s. Charts will render real-time quality scores at 100%.
2. **Induce Quality Failures**: Toggle **Enable Anomalies Mode** to "ACTIVE". The simulator will now intentionally emit null fields, negative numbers, schema mismatches, and massive outlier amounts.
3. **Real-time Observability**:
   - Check the **Active Alerts** panel to see critical error logs popping up.
   - Observe the **Quality Score Trend** graph drop from 100% down to 70-80% due to the penalty formula.
   - Look at the **Issue Breakdown** chart showing the percentages of duplicate vs. missing properties.
4. **Dynamic Configuration**: Click **Edit** on a validation rule (e.g. temperature upper bound or positive amount) inside the rule list, adjust the threshold, and save. The streaming processor adapts instantly without restarting the service.
5. **Inspect DLQ Storage**: Check the local files under `storage/dead-letter/` or `storage/spark-dead-letter/` to verify that rejected records are successfully routed to persistent dead-letter folders.

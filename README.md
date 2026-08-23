# NDMA Disaster Management Authority — Admin Dashboard

A Java Swing desktop application for managing disaster response data — disasters, shelters, agencies, relief operations, beneficiaries, and funding — backed by a MySQL database. Built as a generic admin panel: it auto-detects tables and lets an authenticated admin view, insert, update, and delete records through a GUI.

## Features

- Admin login (falls back to `admin` / `admin123` if no `admin_login` table exists)
- Auto-lists all tables in the connected database
- View, insert, update, and delete rows in any table
- "Resolve Disaster" button — marks a disaster as `Closed`, which triggers automatic archiving (via a MySQL trigger) into a history table

## Requirements

- JDK 8 or higher
- MySQL Server (running locally, or update the connection URL for a remote server)
- MySQL Connector/J (JDBC driver) — [download here](https://dev.mysql.com/downloads/connector/j/)

## Setup

### 1. Set up the database

Run the included `dbms.sql` file — it creates the database, all tables, the trigger, and the two helper functions in one go:

```bash
mysql -u root -p < dbms.sql
```

This creates a database named `NDMA` with:
- Core tables: `Disaster`, `Location`, `Address`, `Disaster_Address`, `Relief_Operation`, `Agency`, `Agency_Contact`, `Resource`, `Relief_Distribution`, `Shelter`, `Beneficiary`, `Beneficiary_Shelter`, `Contact_No`, `Damage_Funding`
- `admin_login` table for authentication
- `previous_disasters` table (archive for resolved disasters)
- `after_disaster_resolved` trigger — automatically logs a disaster to `previous_disasters` and removes it from `Disaster` when its status is set to `Closed`
- `count_ongoing_disasters()` and `shelter_remaining_capacity()` SQL functions

### 2. Set your database credentials

The app reads DB credentials from environment variables (no passwords are hardcoded in the source):

| Variable        | Default (if unset)                                                                                   |
|-----------------|-------------------------------------------------------------------------------------------------------|
| `NDMA_DB_URL`   | `jdbc:mysql://localhost:3306/NDMA?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC`       |
| `NDMA_DB_USER`  | `root`                                                                                                 |
| `NDMA_DB_PASS`  | *(empty)*                                                                                              |

**Windows (Command Prompt), current session only:**
```cmd
set NDMA_DB_USER=root
set NDMA_DB_PASS=your_mysql_password
```

**Windows, persistent across sessions:**
```cmd
setx NDMA_DB_USER "root"
setx NDMA_DB_PASS "your_mysql_password"
```
(`setx` only applies to new terminal windows opened afterward — not the current one.)

**macOS/Linux:**
```bash
export NDMA_DB_USER=root
export NDMA_DB_PASS=your_mysql_password
```

**IntelliJ IDEA:**
Run → Edit Configurations → select the `ThirdSem` run config → Environment variables → add `NDMA_DB_USER` and `NDMA_DB_PASS`.

### 3. Download the MySQL Connector/J jar

Get it from [dev.mysql.com](https://dev.mysql.com/downloads/connector/j/) and place the `.jar` file in the project folder (or note its path for the classpath).

## Running the app

### Command line

```bash
javac ThirdSem.java
java -cp ".;path\to\mysql-connector-j-x.x.x.jar" ThirdSem     # Windows
java -cp ".:path/to/mysql-connector-j-x.x.x.jar" ThirdSem     # macOS/Linux
```

### IntelliJ IDEA

1. Open or clone this repo as a project.
2. Add the MySQL Connector/J jar as a dependency (File → Project Structure → Modules → Dependencies → `+` → JARs), or add it via Maven if using a `pom.xml`.
3. Set environment variables as described above (Run → Edit Configurations).
4. Run `ThirdSem.main()`.

## Login

If you haven't populated `admin_login`, use the fallback credentials:

```
Username: admin
Password: admin123
```

To use real credentials, insert a row into `admin_login`:
```sql
INSERT INTO admin_login (username, password) VALUES ('youradmin', 'yourpassword');
```

## Project structure

```
.
├── ThirdSem.java   # Main application (DB helper + Swing UI)
├── dbms.sql        # Full database setup (tables, trigger, functions)
└── README.md
```

## Notes

- Passwords are never committed to this repo — they're supplied via environment variables at runtime.
- The MySQL Connector/J jar is not included in this repo; download it separately (see Requirements).
- If you change the database name, update `NDMA_DB_URL` accordingly (or edit the default in `ThirdSem.java`).

-- ============================================================
-- NDMA Disaster Management Authority — Full Database Setup
-- Contains: Database creation, all tables, trigger, and functions
-- Run this file to set up the complete database from scratch:
--   mysql -u root -p < dbms.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS NDMA;
USE NDMA;

-- ============================================================
-- ADMIN LOGIN TABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS admin_login (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(100) NOT NULL
);

-- ============================================================
-- CORE TABLES
-- ============================================================
CREATE TABLE IF NOT EXISTS Disaster (
    disaster_id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type ENUM('Manmade','Natural') NOT NULL
);

CREATE TABLE IF NOT EXISTS Location (
    location_id INT PRIMARY KEY,
    city VARCHAR(50) NOT NULL,
    district VARCHAR(50) NOT NULL,
    state VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS Address (
    address_id INT PRIMARY KEY,
    location_id INT NOT NULL,
    street VARCHAR(100) NOT NULL,
    landmark VARCHAR(50),
    pincode CHAR(6),
    FOREIGN KEY (location_id) REFERENCES Location(location_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Disaster_Address (
    disaster_id INT NOT NULL,
    address_id INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    CHECK (end_date IS NULL OR end_date >= start_date),
    severity ENUM('Low','Moderate','High','Extreme') NOT NULL,
    status ENUM('Ongoing','Closed') NOT NULL,
    PRIMARY KEY (disaster_id, address_id),
    FOREIGN KEY (disaster_id) REFERENCES Disaster(disaster_id) ON DELETE CASCADE,
    FOREIGN KEY (address_id) REFERENCES Address(address_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Relief_Operation (
    operation_id INT PRIMARY KEY,
    disaster_id INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    FOREIGN KEY (disaster_id) REFERENCES Disaster(disaster_id) ON DELETE CASCADE,
    CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE TABLE IF NOT EXISTS Agency (
    agency_id INT PRIMARY KEY,
    agency_name VARCHAR(100) NOT NULL,
    agency_type VARCHAR(50),
    person_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    address_id INT,
    primary_contact_no VARCHAR(15),
    FOREIGN KEY (address_id) REFERENCES Address(address_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS Agency_Contact (
    agency_id INT,
    contact_no VARCHAR(15),
    PRIMARY KEY (agency_id, contact_no),
    FOREIGN KEY (agency_id) REFERENCES Agency(agency_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Resource (
    resource_id INT PRIMARY KEY,
    resource_name VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    quantity_total INT,
    quantity_available INT,
    agency_id INT NOT NULL,
    FOREIGN KEY (agency_id) REFERENCES Agency(agency_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Relief_Distribution (
    distribution_id INT PRIMARY KEY,
    resource_id INT NOT NULL,
    quantity_distributed INT,
    distribution_date DATE NOT NULL,
    address_id INT NOT NULL,
    FOREIGN KEY (resource_id) REFERENCES Resource(resource_id) ON DELETE CASCADE,
    FOREIGN KEY (address_id) REFERENCES Address(address_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Shelter (
    shelter_id INT PRIMARY KEY,
    address_id INT,
    shelter_name VARCHAR(100) NOT NULL,
    capacity INT,
    current_occupancy INT,
    FOREIGN KEY (address_id) REFERENCES Address(address_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS Beneficiary (
    person_id INT PRIMARY KEY,
    disaster_id INT,
    name VARCHAR(100) NOT NULL,
    age INT,
    CHECK (age > 0 AND age < 120),
    gender CHAR(1),
    address_id INT,
    FOREIGN KEY (address_id) REFERENCES Address(address_id) ON DELETE SET NULL,
    FOREIGN KEY (disaster_id) REFERENCES Disaster(disaster_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS Beneficiary_Shelter (
    person_id INT NOT NULL,
    shelter_id INT NOT NULL,
    PRIMARY KEY (person_id, shelter_id),
    FOREIGN KEY (person_id) REFERENCES Beneficiary(person_id) ON DELETE CASCADE,
    FOREIGN KEY (shelter_id) REFERENCES Shelter(shelter_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Contact_No (
    person_id INT,
    contact_no VARCHAR(10),
    PRIMARY KEY (person_id, contact_no),
    FOREIGN KEY (person_id) REFERENCES Beneficiary(person_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Damage_Funding (
    record_id INT PRIMARY KEY,
    disaster_id INT NOT NULL,
    address_id INT NOT NULL,
    damage_type ENUM('Infrastructure','Agriculture','Housing','Other') NOT NULL,
    estimated_loss DECIMAL(12,2),
    assessment_date DATE NOT NULL,
    amount_allocated DECIMAL(12,2),
    amount_spent DECIMAL(12,2),
    source ENUM('Government','NGO','Private') NOT NULL,
    date_allocated DATE,
    FOREIGN KEY (disaster_id) REFERENCES Disaster(disaster_id) ON DELETE CASCADE,
    FOREIGN KEY (address_id) REFERENCES Address(address_id) ON DELETE CASCADE,
    CHECK (date_allocated IS NULL OR date_allocated >= assessment_date)
);

-- ============================================================
-- HISTORY TABLE (required by the trigger below)
-- Stores resolved/closed disasters for record-keeping.
-- ============================================================
CREATE TABLE IF NOT EXISTS previous_disasters (
    disaster_id INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    address_id INT NOT NULL,
    severity ENUM('Low','Moderate','High','Extreme') NOT NULL,
    status ENUM('Ongoing','Closed') NOT NULL,
    PRIMARY KEY (disaster_id, address_id)
);

-- ============================================================
-- TRIGGER: after_disaster_resolved
-- Fires when a Disaster_Address row's status changes to 'Closed'.
-- Logs the resolved disaster into previous_disasters, then removes
-- the original entry from Disaster.
-- ============================================================
DELIMITER $$

CREATE TRIGGER after_disaster_resolved
AFTER UPDATE ON Disaster_Address
FOR EACH ROW
BEGIN
    IF NEW.status = 'Closed' AND OLD.status <> 'Closed' THEN
        INSERT INTO previous_disasters
            (disaster_id, start_date, end_date, address_id, severity, status)
        VALUES
            (NEW.disaster_id, NEW.start_date, NEW.end_date, NEW.address_id, NEW.severity, NEW.status);

        DELETE FROM Disaster WHERE disaster_id = NEW.disaster_id;
    END IF;
END$$

DELIMITER ;

-- ============================================================
-- FUNCTION: count_ongoing_disasters
-- Returns the number of disasters currently marked 'Ongoing'.
-- ============================================================
DELIMITER $$

CREATE FUNCTION count_ongoing_disasters()
RETURNS INT
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE ongoing_count INT;
    SELECT COUNT(*) INTO ongoing_count
    FROM Disaster_Address
    WHERE status = 'Ongoing';
    RETURN ongoing_count;
END$$

DELIMITER ;

-- ============================================================
-- FUNCTION: shelter_remaining_capacity
-- Returns total remaining shelter capacity across all shelters
-- (sum of capacity - current_occupancy).
-- ============================================================
DELIMITER $$

CREATE FUNCTION shelter_remaining_capacity()
RETURNS INT
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE remaining INT;
    SELECT COALESCE(SUM(capacity - current_occupancy), 0) INTO remaining
    FROM Shelter;
    RETURN remaining;
END$$

DELIMITER ;

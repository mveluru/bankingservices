-- =============================================================================
-- DATA: the 21 demo bank employees (3 AREA_MANAGER, 6 MANAGER, 12 TELLER) exactly as
-- EmployeeDataSeeder creates them.
--
-- * Run AFTER data/04_seed_bank_locations.sql (branches are looked up by name) and ONLY
--   against an EMPTY bank_employees table (ids 1..21 are explicit; supervisor_id refers to them).
-- * Privileges are not stored: they come from role (TELLER: view/deposit/withdraw; MANAGER: plus
--   suspend/update-suspension/reactivate/close and branch reports; AREA_MANAGER: plus manage employees).
-- * Hire dates are relative to CURDATE(), like the seeder's LocalDate.now().
--
-- Usage:   mysql -u <user> -p < db/data/07_seed_bank_employees.sql
-- =============================================================================

USE db_example;

START TRANSACTION;

INSERT INTO bank_employees (id, employee_number, first_name, last_name, email, phone_number, role, job_title, status, hire_date, bank_location_id, region, supervisor_id) VALUES
  (1, 'EMP-000001', 'Priya', 'Raman', 'priya.raman@brite-bank.example', '512-555-0201', 'AREA_MANAGER', 'Area Manager, Southwest', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 60 MONTH), NULL, 'Southwest', NULL),
  (2, 'EMP-000002', 'Daniel', 'Okafor', 'daniel.okafor@brite-bank.example', '312-555-0202', 'AREA_MANAGER', 'Area Manager, Midwest', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 54 MONTH), NULL, 'Midwest', NULL),
  (3, 'EMP-000003', 'Elena', 'Vasquez', 'elena.vasquez@brite-bank.example', '816-555-0203', 'AREA_MANAGER', 'Area Manager, South Central', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 48 MONTH), NULL, 'South Central', NULL),
  (4, 'EMP-000004', 'Marcus', 'Bell', 'marcus.bell@brite-bank.example', '512-555-0204', 'MANAGER', 'Branch Manager', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 40 MONTH), (SELECT id FROM bank_locations WHERE name = 'Austin Downtown Branch'), NULL, 1),
  (5, 'EMP-000005', 'Hannah', 'Kim', 'hannah.kim@brite-bank.example', '713-555-0205', 'MANAGER', 'Branch Manager', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 36 MONTH), (SELECT id FROM bank_locations WHERE name = 'Houston Galleria Branch'), NULL, 1),
  (6, 'EMP-000006', 'Robert', 'Lindqvist', 'robert.lindqvist@brite-bank.example', '312-555-0206', 'MANAGER', 'Branch Manager', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 44 MONTH), (SELECT id FROM bank_locations WHERE name = 'Chicago Loop Branch'), NULL, 2),
  (7, 'EMP-000007', 'Aisha', 'Rahman', 'aisha.rahman@brite-bank.example', '612-555-0207', 'MANAGER', 'Branch Manager', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 30 MONTH), (SELECT id FROM bank_locations WHERE name = 'Minneapolis Nicollet Branch'), NULL, 2),
  (8, 'EMP-000008', 'Thomas', 'Greene', 'thomas.greene@brite-bank.example', '816-555-0208', 'MANAGER', 'Branch Manager', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 38 MONTH), (SELECT id FROM bank_locations WHERE name = 'Kansas City Plaza Branch'), NULL, 3),
  (9, 'EMP-000009', 'Sofia', 'Marino', 'sofia.marino@brite-bank.example', '615-555-0209', 'MANAGER', 'Branch Manager', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 28 MONTH), (SELECT id FROM bank_locations WHERE name = 'Nashville Music Row Branch'), NULL, 3),
  (10, 'EMP-000010', 'Lucas', 'Meyer', 'lucas.meyer@brite-bank.example', '512-555-0210', 'TELLER', 'Senior Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 26 MONTH), (SELECT id FROM bank_locations WHERE name = 'Austin Downtown Branch'), NULL, 4),
  (11, 'EMP-000011', 'Olivia', 'Chen', 'olivia.chen@brite-bank.example', '512-555-0211', 'TELLER', 'Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 9 MONTH), (SELECT id FROM bank_locations WHERE name = 'Austin Downtown Branch'), NULL, 4),
  (12, 'EMP-000012', 'Jamal', 'Carter', 'jamal.carter@brite-bank.example', '214-555-0212', 'TELLER', 'Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 14 MONTH), (SELECT id FROM bank_locations WHERE name = 'Dallas Main Street Branch'), NULL, 1),
  (13, 'EMP-000013', 'Emma', 'Davis', 'emma.davis@brite-bank.example', '713-555-0213', 'TELLER', 'Senior Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 22 MONTH), (SELECT id FROM bank_locations WHERE name = 'Houston Galleria Branch'), NULL, 5),
  (14, 'EMP-000014', 'Noah', 'Patel', 'noah.patel@brite-bank.example', '713-555-0214', 'TELLER', 'Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 6 MONTH), (SELECT id FROM bank_locations WHERE name = 'Houston Galleria Branch'), NULL, 5),
  (15, 'EMP-000015', 'Grace', 'Nowak', 'grace.nowak@brite-bank.example', '312-555-0215', 'TELLER', 'Senior Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 31 MONTH), (SELECT id FROM bank_locations WHERE name = 'Chicago Loop Branch'), NULL, 6),
  (16, 'EMP-000016', 'Ethan', 'Brooks', 'ethan.brooks@brite-bank.example', '312-555-0216', 'TELLER', 'Teller', 'ON_LEAVE', DATE_SUB(CURDATE(), INTERVAL 12 MONTH), (SELECT id FROM bank_locations WHERE name = 'Chicago Loop Branch'), NULL, 6),
  (17, 'EMP-000017', 'Mia', 'Johansson', 'mia.johansson@brite-bank.example', '612-555-0217', 'TELLER', 'Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 8 MONTH), (SELECT id FROM bank_locations WHERE name = 'Minneapolis Nicollet Branch'), NULL, 7),
  (18, 'EMP-000018', 'Carlos', 'Ortega', 'carlos.ortega@brite-bank.example', '816-555-0218', 'TELLER', 'Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 17 MONTH), (SELECT id FROM bank_locations WHERE name = 'Kansas City Plaza Branch'), NULL, 8),
  (19, 'EMP-000019', 'Layla', 'Hassan', 'layla.hassan@brite-bank.example', '615-555-0219', 'TELLER', 'Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 4 MONTH), (SELECT id FROM bank_locations WHERE name = 'Nashville Music Row Branch'), NULL, 9),
  (20, 'EMP-000020', 'Ben', 'Whitaker', 'ben.whitaker@brite-bank.example', '414-555-0220', 'TELLER', 'Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 11 MONTH), (SELECT id FROM bank_locations WHERE name = 'Milwaukee Wisconsin Ave Branch'), NULL, 2),
  (21, 'EMP-000021', 'Chloe', 'Dubois', 'chloe.dubois@brite-bank.example', '504-555-0221', 'TELLER', 'Senior Teller', 'ACTIVE', DATE_SUB(CURDATE(), INTERVAL 24 MONTH), (SELECT id FROM bank_locations WHERE name = 'New Orleans Canal Street Branch'), NULL, 3);

COMMIT;

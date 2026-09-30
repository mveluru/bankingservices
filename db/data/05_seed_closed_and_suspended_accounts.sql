-- =============================================================================
-- DATA: 40 more demo accounts on top of the 52 in 01_seed_customers_accounts.sql:
--   20 CLOSED    CH-0000030001..30010, SV-0000040001..40010  (balance 0.00)
--   20 SUSPENDED CH-0000050001..50010, SV-0000060001..60010  (flag = 1, start, end or NULL, notes)
-- Exactly what AccountStatusDemoSeeder creates - generated from the same data, so the two stay identical.
--
-- * Run AFTER ddl/01_create_tables.sql (or 03_account_suspension_migration.sql on an older database)
--   and after data/01_seed_customers_accounts.sql. Ids are explicit (53..92; customer N belongs to
--   account N), so it expects ids 53..92 to be free. The unique account_number index makes a rerun fail
--   and roll back, so it can't create duplicates.
-- * Dates are relative to CURDATE()/NOW(), like the seeder. Closed accounts were created 26-40 months
--   ago (outside GET /v1/api/accounts's default 18-month window: use months=48 or createdFrom);
--   suspended ones 4-14 months ago. 14 suspensions end 3-60 days from now, 6 are indefinite (NULL end).
--
-- * Needs customers.phone_number (ddl/01_create_tables.sql, or ddl/04_customer_phone_migration.sql on an older database).
--   Phones are 512-555-<customer id, 4 digits>.
--
-- Usage:   mysql -u <user> -p < db/data/05_seed_closed_and_suspended_accounts.sql
-- =============================================================================

USE db_example;

START TRANSACTION;

INSERT INTO customers (id, first_name, last_name, date_of_birth, phone_number, street, city, state, zip, country, address_line1, address_line2) VALUES
  (53, 'Nora', 'Adams', '1965-01-01', '512-555-0053', '100 Maple St', 'Nashville', 'TN', '37201', 'USA', '100 Maple St', NULL),
  (54, 'Owen', 'Baker', '1972-06-12', '512-555-0054', '113 Oakwood Ave', 'Columbus', 'OH', '43215', 'USA', '113 Oakwood Ave', NULL),
  (55, 'Priya', 'Carter', '1979-11-23', '512-555-0055', '126 Cedar Ln', 'Indianapolis', 'IN', '46204', 'USA', '126 Cedar Ln', NULL),
  (56, 'Quentin', 'Diaz', '1986-04-06', '512-555-0056', '139 Elm Dr', 'Charlotte', 'NC', '28202', 'USA', '139 Elm Dr', NULL),
  (57, 'Rosa', 'Evans', '1993-09-17', '512-555-0057', '152 Birch Rd', 'San Diego', 'CA', '92101', 'USA', '152 Birch Rd', NULL),
  (58, 'Sam', 'Flores', '1965-02-28', '512-555-0058', '165 Sunset Blvd', 'Las Vegas', 'NV', '89101', 'USA', '165 Sunset Blvd', NULL),
  (59, 'Tara', 'Gray', '1972-07-11', '512-555-0059', '178 Lakeview Ct', 'Salt Lake City', 'UT', '84101', 'USA', '178 Lakeview Ct', NULL),
  (60, 'Umar', 'Hughes', '1979-12-22', '512-555-0060', '191 Hillcrest Way', 'Minneapolis', 'MN', '55401', 'USA', '191 Hillcrest Way', NULL),
  (61, 'Vera', 'Irwin', '1986-05-05', '512-555-0061', '204 River Rd', 'Cleveland', 'OH', '44101', 'USA', '204 River Rd', NULL),
  (62, 'Walt', 'James', '1993-10-16', '512-555-0062', '217 Park Ave', 'Pittsburgh', 'PA', '15201', 'USA', '217 Park Ave', NULL),
  (63, 'Xena', 'Kim', '1965-03-27', '512-555-0063', '230 Maple St', 'Baltimore', 'MD', '21201', 'USA', '230 Maple St', NULL),
  (64, 'Yuri', 'Lopez', '1972-08-10', '512-555-0064', '243 Oakwood Ave', 'Milwaukee', 'WI', '53202', 'USA', '243 Oakwood Ave', NULL),
  (65, 'Zelda', 'Moore', '1979-01-21', '512-555-0065', '256 Cedar Ln', 'Sacramento', 'CA', '95814', 'USA', '256 Cedar Ln', NULL),
  (66, 'Andre', 'Nguyen', '1986-06-04', '512-555-0066', '269 Elm Dr', 'Orlando', 'FL', '32801', 'USA', '269 Elm Dr', NULL),
  (67, 'Bianca', 'Owens', '1993-11-15', '512-555-0067', '282 Birch Rd', 'Memphis', 'TN', '38103', 'USA', '282 Birch Rd', NULL),
  (68, 'Caleb', 'Patel', '1965-04-26', '512-555-0068', '295 Sunset Blvd', 'Birmingham', 'AL', '35203', 'USA', '295 Sunset Blvd', NULL),
  (69, 'Dina', 'Quincy', '1972-09-09', '512-555-0069', '308 Lakeview Ct', 'Des Moines', 'IA', '50309', 'USA', '308 Lakeview Ct', NULL),
  (70, 'Emeka', 'Rivera', '1979-02-20', '512-555-0070', '321 Hillcrest Way', 'Little Rock', 'AR', '72201', 'USA', '321 Hillcrest Way', NULL),
  (71, 'Farah', 'Stone', '1986-07-03', '512-555-0071', '334 River Rd', 'Jackson', 'MS', '39201', 'USA', '334 River Rd', NULL),
  (72, 'Gustavo', 'Turner', '1993-12-14', '512-555-0072', '347 Park Ave', 'Honolulu', 'HI', '96813', 'USA', '347 Park Ave', NULL),
  (73, 'Hana', 'Usman', '1965-05-25', '512-555-0073', '360 Maple St', 'Nashville', 'TN', '37201', 'USA', '360 Maple St', NULL),
  (74, 'Ivan', 'Vance', '1972-10-08', '512-555-0074', '373 Oakwood Ave', 'Columbus', 'OH', '43215', 'USA', '373 Oakwood Ave', NULL),
  (75, 'Jada', 'Walker', '1979-03-19', '512-555-0075', '386 Cedar Ln', 'Indianapolis', 'IN', '46204', 'USA', '386 Cedar Ln', NULL),
  (76, 'Kofi', 'Xu', '1986-08-02', '512-555-0076', '399 Elm Dr', 'Charlotte', 'NC', '28202', 'USA', '399 Elm Dr', NULL),
  (77, 'Lena', 'Young', '1993-01-13', '512-555-0077', '412 Birch Rd', 'San Diego', 'CA', '92101', 'USA', '412 Birch Rd', NULL),
  (78, 'Mateo', 'Zimmer', '1965-06-24', '512-555-0078', '425 Sunset Blvd', 'Las Vegas', 'NV', '89101', 'USA', '425 Sunset Blvd', NULL),
  (79, 'Nadia', 'Allen', '1972-11-07', '512-555-0079', '438 Lakeview Ct', 'Salt Lake City', 'UT', '84101', 'USA', '438 Lakeview Ct', NULL),
  (80, 'Omar', 'Bennett', '1979-04-18', '512-555-0080', '451 Hillcrest Way', 'Minneapolis', 'MN', '55401', 'USA', '451 Hillcrest Way', NULL),
  (81, 'Petra', 'Cooper', '1986-09-01', '512-555-0081', '464 River Rd', 'Cleveland', 'OH', '44101', 'USA', '464 River Rd', NULL),
  (82, 'Quinn', 'Dixon', '1993-02-12', '512-555-0082', '477 Park Ave', 'Pittsburgh', 'PA', '15201', 'USA', '477 Park Ave', NULL),
  (83, 'Rafael', 'Ellis', '1965-07-23', '512-555-0083', '490 Maple St', 'Baltimore', 'MD', '21201', 'USA', '490 Maple St', NULL),
  (84, 'Sofia', 'Fisher', '1972-12-06', '512-555-0084', '503 Oakwood Ave', 'Milwaukee', 'WI', '53202', 'USA', '503 Oakwood Ave', NULL),
  (85, 'Tomas', 'Grant', '1979-05-17', '512-555-0085', '516 Cedar Ln', 'Sacramento', 'CA', '95814', 'USA', '516 Cedar Ln', NULL),
  (86, 'Uma', 'Hayes', '1986-10-28', '512-555-0086', '529 Elm Dr', 'Orlando', 'FL', '32801', 'USA', '529 Elm Dr', NULL),
  (87, 'Victor', 'Ingram', '1993-03-11', '512-555-0087', '542 Birch Rd', 'Memphis', 'TN', '38103', 'USA', '542 Birch Rd', NULL),
  (88, 'Willa', 'Jenkins', '1965-08-22', '512-555-0088', '555 Sunset Blvd', 'Birmingham', 'AL', '35203', 'USA', '555 Sunset Blvd', NULL),
  (89, 'Ximena', 'Knight', '1972-01-05', '512-555-0089', '568 Lakeview Ct', 'Des Moines', 'IA', '50309', 'USA', '568 Lakeview Ct', NULL),
  (90, 'Yara', 'Lawson', '1979-06-16', '512-555-0090', '581 Hillcrest Way', 'Little Rock', 'AR', '72201', 'USA', '581 Hillcrest Way', NULL),
  (91, 'Zane', 'Mendez', '1986-11-27', '512-555-0091', '594 River Rd', 'Jackson', 'MS', '39201', 'USA', '594 River Rd', NULL),
  (92, 'Amara', 'Norris', '1993-04-10', '512-555-0092', '607 Park Ave', 'Honolulu', 'HI', '96813', 'USA', '607 Park Ave', NULL);

INSERT INTO accounts (id, account_number, account_type, account_status, balance, created_date, closed_date,
                      suspended, suspended_start, suspended_end, suspension_notes, customer_id, version) VALUES
  (53, 'CH-0000030001', 'CHECKING', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 26 MONTH), DATE_SUB(CURDATE(), INTERVAL 3 MONTH), 0, NULL, NULL, NULL, 53, 0),
  (54, 'CH-0000030002', 'CHECKING', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 29 MONTH), DATE_SUB(CURDATE(), INTERVAL 5 MONTH), 0, NULL, NULL, NULL, 54, 0),
  (55, 'CH-0000030003', 'CHECKING', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 32 MONTH), DATE_SUB(CURDATE(), INTERVAL 7 MONTH), 0, NULL, NULL, NULL, 55, 0),
  (56, 'CH-0000030004', 'CHECKING', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 35 MONTH), DATE_SUB(CURDATE(), INTERVAL 9 MONTH), 0, NULL, NULL, NULL, 56, 0),
  (57, 'CH-0000030005', 'CHECKING', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 38 MONTH), DATE_SUB(CURDATE(), INTERVAL 11 MONTH), 0, NULL, NULL, NULL, 57, 0),
  (58, 'CH-0000030006', 'CHECKING', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 26 MONTH), DATE_SUB(CURDATE(), INTERVAL 13 MONTH), 0, NULL, NULL, NULL, 58, 0),
  (59, 'CH-0000030007', 'CHECKING', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 29 MONTH), DATE_SUB(CURDATE(), INTERVAL 15 MONTH), 0, NULL, NULL, NULL, 59, 0),
  (60, 'CH-0000030008', 'CHECKING', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 32 MONTH), DATE_SUB(CURDATE(), INTERVAL 17 MONTH), 0, NULL, NULL, NULL, 60, 0),
  (61, 'CH-0000030009', 'CHECKING', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 35 MONTH), DATE_SUB(CURDATE(), INTERVAL 19 MONTH), 0, NULL, NULL, NULL, 61, 0),
  (62, 'CH-0000030010', 'CHECKING', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 38 MONTH), DATE_SUB(CURDATE(), INTERVAL 3 MONTH), 0, NULL, NULL, NULL, 62, 0),
  (63, 'SV-0000040001', 'SAVINGS', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 28 MONTH), DATE_SUB(CURDATE(), INTERVAL 4 MONTH), 0, NULL, NULL, NULL, 63, 0),
  (64, 'SV-0000040002', 'SAVINGS', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 31 MONTH), DATE_SUB(CURDATE(), INTERVAL 6 MONTH), 0, NULL, NULL, NULL, 64, 0),
  (65, 'SV-0000040003', 'SAVINGS', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 34 MONTH), DATE_SUB(CURDATE(), INTERVAL 8 MONTH), 0, NULL, NULL, NULL, 65, 0),
  (66, 'SV-0000040004', 'SAVINGS', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 37 MONTH), DATE_SUB(CURDATE(), INTERVAL 10 MONTH), 0, NULL, NULL, NULL, 66, 0),
  (67, 'SV-0000040005', 'SAVINGS', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 40 MONTH), DATE_SUB(CURDATE(), INTERVAL 12 MONTH), 0, NULL, NULL, NULL, 67, 0),
  (68, 'SV-0000040006', 'SAVINGS', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 29 MONTH), DATE_SUB(CURDATE(), INTERVAL 14 MONTH), 0, NULL, NULL, NULL, 68, 0),
  (69, 'SV-0000040007', 'SAVINGS', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 32 MONTH), DATE_SUB(CURDATE(), INTERVAL 16 MONTH), 0, NULL, NULL, NULL, 69, 0),
  (70, 'SV-0000040008', 'SAVINGS', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 35 MONTH), DATE_SUB(CURDATE(), INTERVAL 18 MONTH), 0, NULL, NULL, NULL, 70, 0),
  (71, 'SV-0000040009', 'SAVINGS', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 38 MONTH), DATE_SUB(CURDATE(), INTERVAL 20 MONTH), 0, NULL, NULL, NULL, 71, 0),
  (72, 'SV-0000040010', 'SAVINGS', 'CLOSED', 0.00, DATE_SUB(CURDATE(), INTERVAL 41 MONTH), DATE_SUB(CURDATE(), INTERVAL 5 MONTH), 0, NULL, NULL, NULL, 72, 0),
  (73, 'CH-0000050001', 'CHECKING', 'SUSPENDED', 5808.93, DATE_SUB(CURDATE(), INTERVAL 4 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 'Suspected fraudulent card activity - under review', 73, 0),
  (74, 'CH-0000050002', 'CHECKING', 'SUSPENDED', 371.35, DATE_SUB(CURDATE(), INTERVAL 6 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 'KYC documents expired - awaiting updated ID', 74, 0),
  (75, 'CH-0000050003', 'CHECKING', 'SUSPENDED', 2584.01, DATE_SUB(CURDATE(), INTERVAL 8 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 11 DAY), 'Customer requested temporary freeze (lost debit card)', 75, 0),
  (76, 'CH-0000050004', 'CHECKING', 'SUSPENDED', 2125.42, DATE_SUB(CURDATE(), INTERVAL 10 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, 'Court order - funds frozen pending legal review', 76, 0),
  (77, 'CH-0000050005', 'CHECKING', 'SUSPENDED', 6667.77, DATE_SUB(CURDATE(), INTERVAL 12 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_ADD(NOW(), INTERVAL 19 DAY), 'Compliance hold - unusual wire activity', 77, 0),
  (78, 'CH-0000050006', 'CHECKING', 'SUSPENDED', 6138.79, DATE_SUB(CURDATE(), INTERVAL 14 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_ADD(NOW(), INTERVAL 23 DAY), 'Returned mail - address verification required', 78, 0),
  (79, 'CH-0000050007', 'CHECKING', 'SUSPENDED', 8045.79, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 19 DAY), NULL, 'Possible account takeover reported by customer', 79, 0),
  (80, 'CH-0000050008', 'CHECKING', 'SUSPENDED', 919.41, DATE_SUB(CURDATE(), INTERVAL 7 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 22 DAY), DATE_ADD(NOW(), INTERVAL 31 DAY), 'Chargeback dispute under investigation', 80, 0),
  (81, 'CH-0000050009', 'CHECKING', 'SUSPENDED', 3884.01, DATE_SUB(CURDATE(), INTERVAL 9 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_ADD(NOW(), INTERVAL 35 DAY), 'Sanctions screening alert - manual review', 81, 0),
  (82, 'CH-0000050010', 'CHECKING', 'SUSPENDED', 413.71, DATE_SUB(CURDATE(), INTERVAL 11 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 28 DAY), NULL, 'Customer traveling abroad - requested temporary hold', 82, 0),
  (83, 'SV-0000060001', 'SAVINGS', 'SUSPENDED', 5856.63, DATE_SUB(CURDATE(), INTERVAL 13 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 31 DAY), DATE_ADD(NOW(), INTERVAL 43 DAY), 'Suspected fraudulent card activity - under review', 83, 0),
  (84, 'SV-0000060002', 'SAVINGS', 'SUSPENDED', 12881.20, DATE_SUB(CURDATE(), INTERVAL 4 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 34 DAY), DATE_ADD(NOW(), INTERVAL 47 DAY), 'KYC documents expired - awaiting updated ID', 84, 0),
  (85, 'SV-0000060003', 'SAVINGS', 'SUSPENDED', 1150.13, DATE_SUB(CURDATE(), INTERVAL 6 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 37 DAY), NULL, 'Customer requested temporary freeze (lost debit card)', 85, 0),
  (86, 'SV-0000060004', 'SAVINGS', 'SUSPENDED', 5371.52, DATE_SUB(CURDATE(), INTERVAL 8 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_ADD(NOW(), INTERVAL 55 DAY), 'Court order - funds frozen pending legal review', 86, 0),
  (87, 'SV-0000060005', 'SAVINGS', 'SUSPENDED', 16422.17, DATE_SUB(CURDATE(), INTERVAL 10 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 59 DAY), 'Compliance hold - unusual wire activity', 87, 0),
  (88, 'SV-0000060006', 'SAVINGS', 'SUSPENDED', 13851.07, DATE_SUB(CURDATE(), INTERVAL 12 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, 'Returned mail - address verification required', 88, 0),
  (89, 'SV-0000060007', 'SAVINGS', 'SUSPENDED', 5900.80, DATE_SUB(CURDATE(), INTERVAL 14 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_ADD(NOW(), INTERVAL 9 DAY), 'Possible account takeover reported by customer', 89, 0),
  (90, 'SV-0000060008', 'SAVINGS', 'SUSPENDED', 14937.01, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 13 DAY), 'Chargeback dispute under investigation', 90, 0),
  (91, 'SV-0000060009', 'SAVINGS', 'SUSPENDED', 20331.05, DATE_SUB(CURDATE(), INTERVAL 7 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 17 DAY), 'Sanctions screening alert - manual review', 91, 0),
  (92, 'SV-0000060010', 'SAVINGS', 'SUSPENDED', 659.22, DATE_SUB(CURDATE(), INTERVAL 9 MONTH), NULL, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_ADD(NOW(), INTERVAL 21 DAY), 'Customer traveling abroad - requested temporary hold', 92, 0);

COMMIT;

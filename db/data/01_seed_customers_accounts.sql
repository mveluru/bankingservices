-- =============================================================================
-- DATA: the 52 demo accounts (26 checking, 26 savings) and their customers, exactly as
-- AccountDataSeeder creates them - generated from that class, so the two stay identical.
--
-- * Run ONLY against EMPTY accounts/customers tables. The account_number unique index makes a
--   second run fail (and the transaction below roll back), so it can't create duplicates.
-- * If you load this before starting the app, AccountDataSeeder sees a non-empty accounts
--   table and skips its own seeding - same result either way.
-- * created_date / closed_date are relative to CURDATE(), like the seeder's LocalDate.now(),
--   so the ACTIVE accounts (2-5 months old) stay inside GET /v1/api/accounts's default
--   18-month window; the two CLOSED accounts (CH-0000010004, SV-0000020004) are deliberately
--   24-30 months old, i.e. outside it, to demo the months/createdFrom filters.
-- * Customers have phone numbers 512-555-0001..0052 (customer N = 512-555-000N).
-- * Ids are explicit (1..52; customer N belongs to account N) so 02_sample_transactions.sql
--   and the DML scripts can rely on them. version = 0 is the @Version starting value.
--
-- Usage:   mysql -u <user> -p < db/data/01_seed_customers_accounts.sql
-- =============================================================================

USE db_example;

START TRANSACTION;

-- customers
INSERT INTO customers (id, first_name, last_name, date_of_birth, phone_number, street, city, state, zip, country, address_line1, address_line2) VALUES
  (1, 'Alice', 'Smith', '1985-04-12', '512-555-0001', '123 Main St', 'Austin', 'TX', '78701', 'USA', 'Apt 4B', NULL),
  (2, 'Bob', 'Jones', '1991-11-23', '512-555-0002', '456 Oak Ln', 'Dallas', 'TX', '75201', 'USA', '456 Oak Ln', NULL),
  (3, 'Carol', 'Davis', '1978-03-15', '512-555-0003', '500 5th Ave', 'Denver', 'CO', '80202', 'USA', '500 5th Ave', NULL),
  (4, 'David', 'Miller', '1994-08-19', '512-555-0004', '789 Pine Rd', 'Houston', 'TX', '77001', 'USA', '789 Pine Rd', NULL),
  (5, 'Emma', 'Wilson', '1988-12-01', '512-555-0005', '200 2nd St', 'Seattle', 'WA', '98101', 'USA', '200 2nd St', NULL),
  (6, 'Frank', 'Garcia', '1975-06-23', '512-555-0006', '100 Ocean Dr', 'Miami', 'FL', '33101', 'USA', '100 Ocean Dr', NULL),
  (7, 'Grace', 'Lee', '1990-01-30', '512-555-0007', '300 Lake Shore Dr', 'Chicago', 'IL', '60601', 'USA', '300 Lake Shore Dr', NULL),
  (8, 'Henry', 'Martinez', '1982-09-05', '512-555-0008', '150 Desert Rd', 'Phoenix', 'AZ', '85001', 'USA', '150 Desert Rd', NULL),
  (9, 'Ivy', 'Chen', '1996-04-11', '512-555-0009', '45 Beacon St', 'Boston', 'MA', '02101', 'USA', '45 Beacon St', NULL),
  (10, 'Jack', 'Robinson', '1970-11-27', '512-555-0010', '10 Peachtree St', 'Atlanta', 'GA', '30301', 'USA', '10 Peachtree St', NULL),
  (11, 'Karen', 'White', '1985-07-14', '512-555-0011', '25 Pine St', 'Portland', 'OR', '97201', 'USA', '25 Pine St', NULL),
  (12, 'Liam', 'Thompson', '1993-02-08', '512-555-0012', '600 Colfax Ave', 'Denver', 'CO', '80203', 'USA', '600 Colfax Ave', NULL),
  (13, 'Xavier', 'Brooks', '1986-05-21', '512-555-0013', '12 Birch St', 'Raleigh', 'NC', '27601', 'USA', '12 Birch St', NULL),
  (14, 'Yolanda', 'Reyes', '1991-09-14', '512-555-0014', '77 Cedar Ave', 'Tucson', 'AZ', '85701', 'USA', '77 Cedar Ave', NULL),
  (15, 'Zachary', 'Foster', '1979-02-03', '512-555-0015', '300 Elm St', 'Kansas City', 'MO', '64101', 'USA', '300 Elm St', NULL),
  (16, 'Amanda', 'Price', '1997-07-30', '512-555-0016', '88 Willow Dr', 'Omaha', 'NE', '68101', 'USA', '88 Willow Dr', NULL),
  (17, 'Brian', 'Coleman', '1984-11-08', '512-555-0017', '45 Aspen Ln', 'Boise', 'ID', '83701', 'USA', '45 Aspen Ln', NULL),
  (18, 'Cynthia', 'Ortiz', '1990-04-25', '512-555-0018', '210 Sunset Blvd', 'Fresno', 'CA', '93701', 'USA', '210 Sunset Blvd', NULL),
  (19, 'Daniel', 'Reed', '1976-08-17', '512-555-0019', '63 Magnolia St', 'Tulsa', 'OK', '74101', 'USA', '63 Magnolia St', NULL),
  (20, 'Elena', 'Vargas', '1993-12-09', '512-555-0020', '18 Riverside Dr', 'Albuquerque', 'NM', '87101', 'USA', '18 Riverside Dr', NULL),
  (21, 'Felix', 'Ward', '1981-03-27', '512-555-0021', '500 Highland Ave', 'Louisville', 'KY', '40201', 'USA', '500 Highland Ave', NULL),
  (22, 'Gina', 'Torres', '1988-06-13', '512-555-0022', '27 Meadow Ln', 'Baton Rouge', 'LA', '70801', 'USA', '27 Meadow Ln', NULL),
  (23, 'Hassan', 'Ali', '1992-10-22', '512-555-0023', '140 Grove St', 'Richmond', 'VA', '23218', 'USA', '140 Grove St', NULL),
  (24, 'Isabel', 'Cruz', '1987-01-19', '512-555-0024', '9 Harbor Way', 'Providence', 'RI', '02901', 'USA', '9 Harbor Way', NULL),
  (25, 'Jerome', 'Bell', '1975-05-06', '512-555-0025', '310 Union St', 'Hartford', 'CT', '06101', 'USA', '310 Union St', NULL),
  (26, 'Kayla', 'Simmons', '1995-09-02', '512-555-0026', '72 Fairview Rd', 'Madison', 'WI', '53701', 'USA', '72 Fairview Rd', NULL),
  (27, 'Louis', 'Fischer', '1983-12-30', '512-555-0027', '205 Chestnut St', 'Des Moines', 'IA', '50301', 'USA', '205 Chestnut St', NULL),
  (28, 'Maria', 'Rodriguez', '1980-05-19', '512-555-0028', '12 Elm St', 'Dallas', 'TX', '75201', 'USA', '12 Elm St', NULL),
  (29, 'Noah', 'Anderson', '1992-10-03', '512-555-0029', '88 Broadway', 'San Diego', 'CA', '92101', 'USA', '88 Broadway', NULL),
  (30, 'Olivia', 'Harris', '1987-03-22', '512-555-0030', '5 Music Row', 'Nashville', 'TN', '37201', 'USA', '5 Music Row', NULL),
  (31, 'Peter', 'Clark', '1976-12-15', '512-555-0031', '300 High St', 'Columbus', 'OH', '43201', 'USA', '300 High St', NULL),
  (32, 'Quinn', 'Lewis', '1995-06-09', '512-555-0032', '700 Congress Ave', 'Austin', 'TX', '78702', 'USA', '700 Congress Ave', NULL),
  (33, 'Rachel', 'Walker', '1983-08-27', '512-555-0033', '40 Trade St', 'Charlotte', 'NC', '28201', 'USA', '40 Trade St', NULL),
  (34, 'Samuel', 'Young', '1971-01-12', '512-555-0034', '120 Fremont St', 'Las Vegas', 'NV', '89101', 'USA', '120 Fremont St', NULL),
  (35, 'Tina', 'Hall', '1998-09-30', '512-555-0035', '9 Orange Ave', 'Orlando', 'FL', '32801', 'USA', '9 Orange Ave', NULL),
  (36, 'Victor', 'King', '1989-04-18', '512-555-0036', '60 Nicollet Mall', 'Minneapolis', 'MN', '55401', 'USA', '60 Nicollet Mall', NULL),
  (37, 'Wendy', 'Scott', '1974-11-02', '512-555-0037', '15 Capitol Mall', 'Sacramento', 'CA', '95814', 'USA', '15 Capitol Mall', NULL),
  (38, 'Monica', 'Diaz', '1986-02-14', '512-555-0038', '44 Lakeview Dr', 'Salt Lake City', 'UT', '84101', 'USA', '44 Lakeview Dr', NULL),
  (39, 'Nathan', 'Brooks', '1990-06-28', '512-555-0039', '77 Ridge Rd', 'Little Rock', 'AR', '72201', 'USA', '77 Ridge Rd', NULL),
  (40, 'Priya', 'Patel', '1993-10-11', '512-555-0040', '212 Sunrise Ave', 'Anchorage', 'AK', '99501', 'USA', '212 Sunrise Ave', NULL),
  (41, 'Oscar', 'Delgado', '1979-04-07', '512-555-0041', '63 Pinecrest Ln', 'Spokane', 'WA', '99201', 'USA', '63 Pinecrest Ln', NULL),
  (42, 'Paula', 'Nguyen', '1996-08-23', '512-555-0042', '18 Bayview Ter', 'Honolulu', 'HI', '96801', 'USA', '18 Bayview Ter', NULL),
  (43, 'Ryan', 'Mitchell', '1985-12-05', '512-555-0043', '300 Foothill Blvd', 'Reno', 'NV', '89501', 'USA', '300 Foothill Blvd', NULL),
  (44, 'Sofia', 'Ramirez', '1991-03-16', '512-555-0044', '55 Garden St', 'Albany', 'NY', '12201', 'USA', '55 Garden St', NULL),
  (45, 'Trevor', 'Hughes', '1977-07-01', '512-555-0045', '140 Maple Ave', 'Burlington', 'VT', '05401', 'USA', '140 Maple Ave', NULL),
  (46, 'Ursula', 'Bennett', '1994-11-29', '512-555-0046', '9 Overlook Dr', 'Jackson', 'MS', '39201', 'USA', '9 Overlook Dr', NULL),
  (47, 'Vincent', 'Nolan', '1982-05-20', '512-555-0047', '270 Canyon Rd', 'Cheyenne', 'WY', '82001', 'USA', '270 Canyon Rd', NULL),
  (48, 'Wanda', 'Perry', '1989-09-09', '512-555-0048', '38 Brookside Ave', 'Fargo', 'ND', '58102', 'USA', '38 Brookside Ave', NULL),
  (49, 'Xiomara', 'Lopez', '1997-01-26', '512-555-0049', '115 Southgate Dr', 'Wichita', 'KS', '67201', 'USA', '115 Southgate Dr', NULL),
  (50, 'Yusuf', 'Ibrahim', '1984-06-04', '512-555-0050', '6 Lighthouse Rd', 'Portland', 'ME', '04101', 'USA', '6 Lighthouse Rd', NULL),
  (51, 'Zoe', 'Campbell', '1992-10-18', '512-555-0051', '82 Timber Ln', 'Charleston', 'WV', '25301', 'USA', '82 Timber Ln', NULL),
  (52, 'Aaron', 'Blake', '1980-02-27', '512-555-0052', '29 Windsor Ct', 'Manchester', 'NH', '03101', 'USA', '29 Windsor Ct', NULL);

-- accounts
INSERT INTO accounts (id, account_number, account_type, account_status, balance, created_date, closed_date, customer_id, version) VALUES
  (1, 'CH-0000088291', 'CHECKING', 'ACTIVE', 2450.75, DATE_SUB(CURDATE(), INTERVAL 2 MONTH), NULL, 1, 0),
  (2, 'SV-0000044102', 'SAVINGS', 'ACTIVE', 12800.00, DATE_SUB(CURDATE(), INTERVAL 4 MONTH), NULL, 2, 0),
  (3, 'CH-0000010001', 'CHECKING', 'ACTIVE', 3200.50, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 3, 0),
  (4, 'CH-0000010002', 'CHECKING', 'ACTIVE', 1875.20, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 4, 0),
  (5, 'CH-0000010003', 'CHECKING', 'ACTIVE', 4620.00, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 5, 0),
  (6, 'CH-0000010004', 'CHECKING', 'CLOSED', 980.35, DATE_SUB(CURDATE(), INTERVAL 30 MONTH), DATE_SUB(CURDATE(), INTERVAL 6 MONTH), 6, 0),
  (7, 'CH-0000010005', 'CHECKING', 'ACTIVE', 6120.75, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 7, 0),
  (8, 'CH-0000010006', 'CHECKING', 'ACTIVE', 2340.60, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 8, 0),
  (9, 'CH-0000010007', 'CHECKING', 'ACTIVE', 1500.00, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 9, 0),
  (10, 'CH-0000010008', 'CHECKING', 'ACTIVE', 7890.10, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 10, 0),
  (11, 'CH-0000010009', 'CHECKING', 'ACTIVE', 3450.90, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 11, 0),
  (12, 'CH-0000010010', 'CHECKING', 'ACTIVE', 2100.45, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 12, 0),
  (13, 'CH-0000010011', 'CHECKING', 'ACTIVE', 5430.00, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 13, 0),
  (14, 'CH-0000010012', 'CHECKING', 'ACTIVE', 2890.60, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 14, 0),
  (15, 'CH-0000010013', 'CHECKING', 'ACTIVE', 6710.25, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 15, 0),
  (16, 'CH-0000010014', 'CHECKING', 'ACTIVE', 1980.40, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 16, 0),
  (17, 'CH-0000010015', 'CHECKING', 'ACTIVE', 4560.15, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 17, 0),
  (18, 'CH-0000010016', 'CHECKING', 'ACTIVE', 3320.90, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 18, 0),
  (19, 'CH-0000010017', 'CHECKING', 'ACTIVE', 7120.00, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 19, 0),
  (20, 'CH-0000010018', 'CHECKING', 'ACTIVE', 2450.55, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 20, 0),
  (21, 'CH-0000010019', 'CHECKING', 'ACTIVE', 5980.30, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 21, 0),
  (22, 'CH-0000010020', 'CHECKING', 'ACTIVE', 3140.70, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 22, 0),
  (23, 'CH-0000010021', 'CHECKING', 'ACTIVE', 6890.45, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 23, 0),
  (24, 'CH-0000010022', 'CHECKING', 'ACTIVE', 2670.80, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 24, 0),
  (25, 'CH-0000010023', 'CHECKING', 'ACTIVE', 4980.20, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 25, 0),
  (26, 'CH-0000010024', 'CHECKING', 'ACTIVE', 3760.10, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 26, 0),
  (27, 'CH-0000010025', 'CHECKING', 'ACTIVE', 5210.65, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 27, 0),
  (28, 'SV-0000020001', 'SAVINGS', 'ACTIVE', 15200.00, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 28, 0),
  (29, 'SV-0000020002', 'SAVINGS', 'ACTIVE', 8900.50, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 29, 0),
  (30, 'SV-0000020003', 'SAVINGS', 'ACTIVE', 22000.75, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 30, 0),
  (31, 'SV-0000020004', 'SAVINGS', 'CLOSED', 5600.30, DATE_SUB(CURDATE(), INTERVAL 24 MONTH), DATE_SUB(CURDATE(), INTERVAL 3 MONTH), 31, 0),
  (32, 'SV-0000020005', 'SAVINGS', 'ACTIVE', 13400.00, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 32, 0),
  (33, 'SV-0000020006', 'SAVINGS', 'ACTIVE', 9800.60, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 33, 0),
  (34, 'SV-0000020007', 'SAVINGS', 'ACTIVE', 30500.00, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 34, 0),
  (35, 'SV-0000020008', 'SAVINGS', 'ACTIVE', 4200.15, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 35, 0),
  (36, 'SV-0000020009', 'SAVINGS', 'ACTIVE', 17650.40, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 36, 0),
  (37, 'SV-0000020010', 'SAVINGS', 'ACTIVE', 6700.25, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 37, 0),
  (38, 'SV-0000020011', 'SAVINGS', 'ACTIVE', 11200.35, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 38, 0),
  (39, 'SV-0000020012', 'SAVINGS', 'ACTIVE', 8650.90, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 39, 0),
  (40, 'SV-0000020013', 'SAVINGS', 'ACTIVE', 19800.00, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 40, 0),
  (41, 'SV-0000020014', 'SAVINGS', 'ACTIVE', 7340.55, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 41, 0),
  (42, 'SV-0000020015', 'SAVINGS', 'ACTIVE', 14500.20, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 42, 0),
  (43, 'SV-0000020016', 'SAVINGS', 'ACTIVE', 9990.10, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 43, 0),
  (44, 'SV-0000020017', 'SAVINGS', 'ACTIVE', 23100.75, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 44, 0),
  (45, 'SV-0000020018', 'SAVINGS', 'ACTIVE', 6420.40, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 45, 0),
  (46, 'SV-0000020019', 'SAVINGS', 'ACTIVE', 16750.00, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 46, 0),
  (47, 'SV-0000020020', 'SAVINGS', 'ACTIVE', 10230.85, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 47, 0),
  (48, 'SV-0000020021', 'SAVINGS', 'ACTIVE', 13890.60, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 48, 0),
  (49, 'SV-0000020022', 'SAVINGS', 'ACTIVE', 8100.25, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 49, 0),
  (50, 'SV-0000020023', 'SAVINGS', 'ACTIVE', 20450.90, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 50, 0),
  (51, 'SV-0000020024', 'SAVINGS', 'ACTIVE', 7560.35, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 51, 0),
  (52, 'SV-0000020025', 'SAVINGS', 'ACTIVE', 12980.50, DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, 52, 0);

COMMIT;

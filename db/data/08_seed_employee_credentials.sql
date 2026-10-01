-- =============================================================================
-- DATA: a login for each of the 21 demo employees, as EmployeeCredentialSeeder creates them.
--
-- * Run AFTER data/07_seed_bank_employees.sql and ONLY against an EMPTY bank_employee_credentials table.
-- * DEMO ONLY: username = the email's local part (lucas.meyer), password = 2026 + the four-digit employee
--   sequence (EMP-000010 -> 20260010). Only the BCrypt hash is stored (the app verifies these hashes;
--   $2y$ and $2a$/$2b$ are interchangeable for BCrypt). Never reuse this scheme for real people.
--
-- Usage:   mysql -u <user> -p < db/data/08_seed_employee_credentials.sql
-- =============================================================================

USE db_example;

START TRANSACTION;

INSERT INTO bank_employee_credentials (employee_id, username, password_hash, failed_attempts, password_changed_at)
SELECT e.id, v.username, v.password_hash, 0, v.changed_at
  FROM (
    SELECT 'EMP-000001' AS employee_number, 'priya.raman' AS username, '$2y$10$uV8WL3bgv2kh9CBK22T0s.D8QghpRvB.yvgZ0.Fv3pdvYACQ66ZHW' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000002' AS employee_number, 'daniel.okafor' AS username, '$2y$10$7frmgJm.WXbPZv3N33oxtOuBXex1wg9UT2Lj.Y03r9dtkXGsGDG5C' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000003' AS employee_number, 'elena.vasquez' AS username, '$2y$10$05t/Ea9QQbRFymf.e0FSUOnJioJ4hLlpQC98VP7IDPvVXaDsZgBCi' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000004' AS employee_number, 'marcus.bell' AS username, '$2y$10$yHuK8Ak40JiYhvZR0TvpYutUw.hfUmpuFmfvoSl7TX4FPcRN/V1em' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000005' AS employee_number, 'hannah.kim' AS username, '$2y$10$9LxgPLDwXcltwl9.lMobCec5xCe6CWJ3q83JtTqYOg3wkt1R6IFP.' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000006' AS employee_number, 'robert.lindqvist' AS username, '$2y$10$2gslHYaskSjM5DVyTeJd/Oguscu8W277NwgtFYsUH2Mz3DCjnR0FO' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000007' AS employee_number, 'aisha.rahman' AS username, '$2y$10$dhPnio0rnjwlC6kJQfLkTuzKRGxFMprsDKN/tNXUyfQ9rndSUaNqi' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000008' AS employee_number, 'thomas.greene' AS username, '$2y$10$7uUaIC29rE/pxHLCsGzEBu5tCGt7tOCMdZvOavYUnnNti.6qCINEO' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000009' AS employee_number, 'sofia.marino' AS username, '$2y$10$6vcCPUPIR/gC8wAr9efpxuWujE0t9jj0pedEoSylP2R5lqrgWr5uy' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000010' AS employee_number, 'lucas.meyer' AS username, '$2y$10$azDwpYAczfqV5Jj8u8fQm.fjuix23SLpEYKytXay8HtviyBoRjRBG' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000011' AS employee_number, 'olivia.chen' AS username, '$2y$10$bqMGm2NamPCuss3QiWLLR.O0/BNCzKpEC7Ho31VLmotTU7uBYlTH6' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000012' AS employee_number, 'jamal.carter' AS username, '$2y$10$g6kTql5ccfJ5DyrvxzXDNeDwxRskEEVb.Ki8ZP/OqCLRSWtEqUsdu' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000013' AS employee_number, 'emma.davis' AS username, '$2y$10$YeSDq14IqGkHYFJMr7M0xu7vU1iFrlF4Ti.LfGL5pBlGeKD0HUeH6' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000014' AS employee_number, 'noah.patel' AS username, '$2y$10$8/xs2dDfCLqc0LXgsuXwa.buHyQ7B6MQrUgIvog.erLwQM/jG5Vuq' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000015' AS employee_number, 'grace.nowak' AS username, '$2y$10$i95bkn0jNlVeceGJvsXK5eICph6olYEO81Hc4G2YZSn4Uslr2udj.' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000016' AS employee_number, 'ethan.brooks' AS username, '$2y$10$AzA7MeinKOZOYyxrAvNw9OnvBbf928duL22t8xBK8i.Gz6wtRwAcm' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000017' AS employee_number, 'mia.johansson' AS username, '$2y$10$82MdES31Tz4JufBIfNB9MOAySUfE73NbIpEAhyrhiCDKqHLhPOZvK' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000018' AS employee_number, 'carlos.ortega' AS username, '$2y$10$ob0j1SbaMB8Whupjxmi./ehO6KMPbQeLmXCILlgG6rKJpUxIKRswm' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000019' AS employee_number, 'layla.hassan' AS username, '$2y$10$Am08TUFIQTZ3HW7lw8AwmOAxtqk8zvR9W/DJnAJKfKpR7XnewaZNu' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000020' AS employee_number, 'ben.whitaker' AS username, '$2y$10$eh63G0S5T/DTlqHTeCLDjuvJ4Af8ZGuAxk3P9rNU.r.POeloEnel.' AS password_hash, NOW() AS changed_at
    UNION ALL
    SELECT 'EMP-000021' AS employee_number, 'chloe.dubois' AS username, '$2y$10$gmnK7x49S3t7ULZmKcbeQOSy/.qObkTIsbIbxD/I6tZ.3t7I1m7b.' AS password_hash, NOW() AS changed_at
  ) v
  JOIN bank_employees e ON e.employee_number = v.employee_number;

COMMIT;

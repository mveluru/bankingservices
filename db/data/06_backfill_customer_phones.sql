-- =============================================================================
-- DATA: give the demo customers the phone numbers the seeders create for a fresh database, keyed by ACCOUNT
-- (customer ids can drift from account ids once rows have been deleted, account numbers can't):
--
--   * the 52 base accounts (account id 1..52)            -> 512-555-<account id as 4 digits>   (0001..0052)
--   * CH-0000030001..30010 (closed checking)             -> 512-555-0053..0062
--   * SV-0000040001..40010 (closed savings)              -> 512-555-0063..0072
--   * CH-0000050001..50010 (suspended checking)          -> 512-555-0073..0082
--   * SV-0000060001..60010 (suspended savings)           -> 512-555-0083..0092
--
-- * Only touches customers whose phone_number IS NULL, so it is safe to rerun and never overwrites a number.
-- * Customers of accounts registered through the API are left alone on purpose.
-- * Run after ddl/04_customer_phone_migration.sql (or once the app has added the column).
-- * Not needed on a database seeded after this feature; the seed scripts/seeders already include the phones.
--
-- Usage:   mysql -u <user> -p < db/data/06_backfill_customer_phones.sql
-- =============================================================================

USE db_example;

UPDATE customers c
  JOIN accounts a ON a.customer_id = c.id
   SET c.phone_number = CONCAT('512-555-', LPAD(
           CASE
             WHEN a.account_number REGEXP '^(CH|SV)-00000[3-6]00(0[1-9]|10)$'
               THEN 53 + (FLOOR(CAST(SUBSTRING(a.account_number, 4) AS UNSIGNED) / 10000) - 3) * 10
                       + (MOD(CAST(SUBSTRING(a.account_number, 4) AS UNSIGNED), 10000) - 1)
             ELSE a.id
           END, 4, '0'))
 WHERE c.phone_number IS NULL
   AND (a.id <= 52 OR a.account_number REGEXP '^(CH|SV)-00000[3-6]00(0[1-9]|10)$');
SELECT ROW_COUNT() AS customers_updated;

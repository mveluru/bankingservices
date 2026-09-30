-- =============================================================================
-- DDL: creates the schema the Spring Boot app uses (MySQL 8, InnoDB).
--
-- Generated from the JPA entities (org.bee.banking.entity.*, org.bee.events.dto.EventEntity)
-- with Hibernate 6.6's MySQLDialect and the app's naming strategy - i.e. the same DDL that
-- `spring.jpa.hibernate.ddl-auto: update` produces on a fresh database. Constraint names are
-- Hibernate's own, so a later `ddl-auto: update` sees an identical schema and adds nothing.
--
-- Not needed to run the app (ddl-auto creates/updates these tables itself); use it to build
-- the schema by hand or in another environment.
--
-- Usage:   mysql -u <user> -p < db/ddl/01_create_tables.sql
-- =============================================================================

CREATE DATABASE IF NOT EXISTS db_example;
USE db_example;

CREATE TABLE IF NOT EXISTS customers (
    date_of_birth date,
    id bigint not null auto_increment,
    address_line1 varchar(255),
    address_line2 varchar(255),
    city varchar(255),
    country varchar(255),
    first_name varchar(255),
    last_name varchar(255),
    phone_number varchar(20),
    state varchar(255),
    street varchar(255),
    zip varchar(255),
    primary key (id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS accounts (
    balance decimal(38,2),
    closed_date date,
    created_date date,
    customer_id bigint,
    id bigint not null auto_increment,
    suspended bit not null default b'0',  -- Hibernate emits no DEFAULT; it keeps hand-run INSERTs that omit the column valid
    suspended_end datetime(6),
    suspended_start datetime(6),
    version bigint,
    account_number varchar(20) not null,
    account_status enum ('ACTIVE','CLOSED','SUSPENDED'),
    suspension_notes varchar(500),
    account_type enum ('CHECKING','CREDIT_OR_LOAN','INVESTMENT','RETIREMENT','SAVINGS'),
    primary key (id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS account_transactions (
    amount decimal(38,2),
    balance_after decimal(38,2),
    transaction_date date,
    account_id bigint not null,
    id bigint not null auto_increment,
    deposit_type varchar(255),
    transaction_type enum ('DEPOSIT','WITHDRAWAL'),
    primary key (id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS withdrawal_history (
    amount decimal(38,2),
    withdrawal_date date,
    account_id bigint not null,
    id bigint not null auto_increment,
    address_line1 varchar(255),
    address_line2 varchar(255),
    city varchar(255),
    country varchar(255),
    first_name varchar(255),
    last_name varchar(255),
    state varchar(255),
    status varchar(255),
    street varchar(255),
    zip varchar(255),
    primary key (id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS bank_locations (
    closes_at time(6),
    opens_at time(6),
    id bigint not null auto_increment,
    phone_number varchar(20),
    time_zone varchar(40) not null,
    address_line1 varchar(255),
    address_line2 varchar(255),
    city varchar(255),
    country varchar(255),
    name varchar(255) not null,
    state varchar(255),
    zip varchar(255),
    location_type enum ('ATM','BOTH','OFFICE') not null,
    primary key (id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS bank_location_services (
    bank_location_id bigint not null,
    service enum ('ATM_CASH_WITHDRAWAL','ATM_DEPOSIT','BANKING','FOREIGN_EXCHANGE','LOANS_MORTGAGES','NOTARY','SAFE_DEPOSIT_LOCKER','WIRE_TRANSFER') not null,
    primary key (bank_location_id, service)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS events (
    timestamp datetime(6),
    email varchar(255),
    event_id varchar(255) not null,
    user_id varchar(255),
    version varchar(255),
    primary key (event_id)
) engine=InnoDB;

-- Constraints (kept out of CREATE TABLE so table order doesn't matter).
alter table accounts
   add constraint idx_accounts_account_number unique (account_number);

alter table account_transactions
   add constraint FK1n6ys6f08bm5km34nlb09fs2y
   foreign key (account_id)
   references accounts (id);

alter table accounts
   add constraint FKn6x8pdp50os8bq5rbb792upse
   foreign key (customer_id)
   references customers (id);

alter table bank_location_services
   add constraint FKqy6h5bqixyoyin6roe8s2hhmf
   foreign key (bank_location_id)
   references bank_locations (id);

alter table withdrawal_history
   add constraint FKiqdmc315ip4x3pj5y3qog2bpi
   foreign key (account_id)
   references accounts (id);

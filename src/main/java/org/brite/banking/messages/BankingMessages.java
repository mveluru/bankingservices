package org.brite.banking.messages;

public final class BankingMessages {

    private BankingMessages() {
    }

    // Exception messages (String.format-style, %s placeholders)
    public static final String ACCOUNT_NUMBER_REQUIRED = "Account number must be provided and start with CH or SV";
    public static final String WITHDRAWAL_AMOUNT_POSITIVE = "Withdrawal amount must be positive";
    public static final String DEPOSIT_AMOUNT_POSITIVE = "Deposit amount must be positive";
    public static final String UNRECOGNIZED_ACCOUNT_PREFIX = "Unrecognized account number prefix: %s";
    public static final String ACCOUNT_TYPE_MISMATCH = "Requested account type does not match account number";
    public static final String DEPOSIT_TYPE_INVALID = "Deposit type must be 'cash' or 'check'";
    public static final String INSUFFICIENT_FUNDS = "Insufficient funds in account %s";
    public static final String ACCOUNT_NOT_FOUND = "Account not found: %s";
    public static final String ACCOUNT_SUSPENDED = "Account %s is suspended and cannot be used for transactions until it is reactivated";
    public static final String ACCOUNT_ALREADY_SUSPENDED = "Account %s is already suspended";
    public static final String ACCOUNT_NOT_SUSPENDED = "Account %s is not suspended";
    public static final String ACCOUNT_CLOSED_CANNOT_SUSPEND = "Account %s is closed and cannot be suspended";
    public static final String SUSPENSION_START_IN_FUTURE = "Suspension start (%s) cannot be in the future";
    public static final String SUSPENSION_END_NOT_AFTER_START = "Suspension end (%s) must be after suspension start (%s)";
    public static final String SUSPENSION_END_IN_PAST = "Suspension end (%s) must be in the future";
    public static final String SUSPENSION_UPDATE_EMPTY = "Provide notes and/or endDateTime to update the suspension";
    public static final String EMPLOYEE_HEADER_REQUIRED = "Header %s with the acting employee number is required";
    public static final String EMPLOYEE_NOT_FOUND = "Employee not found: %s";
    public static final String EMPLOYEE_NOT_ACTIVE = "Employee %s is %s and cannot perform this action";
    public static final String EMPLOYEE_NOT_AUTHORIZED = "Employee %s (%s) is not authorized: requires %s";
    public static final String UNSUPPORTED_EMPLOYEE_SORT_PROPERTY = "Unsupported sort property: %s (supported: lastName, firstName, employeeNumber, role, hireDate)";
    public static final String INVALID_CREDENTIALS = "Invalid username or password";
    public static final String EMPLOYEE_LOGIN_NOT_ACTIVE = "Employee %s login is %s; only an ACTIVE login can perform transactions";
    public static final String EMPLOYEE_HAS_NO_LOGIN = "Employee %s has no login; only an ACTIVE login can perform transactions";
    public static final String CUSTOMER_LOGIN_NOT_ACTIVE = "Customer login is %s; only an ACTIVE login can perform transactions";
    public static final String LOGIN_STATUS_REQUIRED = "status is required (ACTIVE, INACTIVE, LOCKED or SUSPENDED)";
    public static final String LOGIN_NOT_FOUND = "No login found for %s %s";
    public static final String LOGIN_STATUS_REASON_TOO_LONG = "Reason must be at most 200 characters";
    public static final String CUSTOMER_NOT_FOUND = "Customer not found: %s";
    public static final String CUSTOMER_HAS_LOGIN = "Customer %s already has a login";
    public static final String EMPLOYEE_LOCKED = "Too many failed login attempts; locked until %s";
    public static final String EMPLOYEE_USERNAME_INVALID = "Username must be 3-50 characters: lowercase letters, digits, '.', '_' or '-'";
    public static final String EMPLOYEE_PASSWORD_INVALID = "Password must be exactly 8 digits";
    public static final String EMPLOYEE_USERNAME_TAKEN = "Username %s is already taken";
    public static final String EMPLOYEE_HAS_LOGIN = "Employee %s already has a login";
    public static final String PORTAL_ACTIVITY_DAYS_INVALID = "days must be between 1 and %d";
    public static final String LOCATION_NOT_FOUND = "Bank location not found: %s";
    public static final String ACCOUNT_CLOSED = "Account %s is closed and cannot be used for transactions";
    public static final String ACCOUNT_ALREADY_CLOSED = "Account %s is already closed";
    public static final String UNSUPPORTED_SORT_PROPERTY = "Unsupported sort property: %s (supported: createdDate, closedDate, accountStatus, accountNumber)";
    public static final String UNSUPPORTED_LOCATION_SORT_PROPERTY = "Unsupported sort property: %s (supported: name, locationType, city, state)";
    public static final String CREATED_DATE_RANGE_INVALID = "createdFrom (%s) must not be after createdTo (%s)";
    public static final String CLOSED_DATE_RANGE_INVALID = "closedFrom (%s) must not be after closedTo (%s)";
    public static final String MONTHS_MUST_BE_POSITIVE = "months must be positive, got %d";
    public static final String MINIMUM_AGE_VIOLATION = "Customer must be at least %d years old to open an account";
    public static final String MAX_CASH_DEPOSIT_EXCEEDED = "Cash deposits cannot exceed %s";
    public static final String MIN_BALANCE_VIOLATION = "Withdrawal declined: account %s must retain a minimum balance of %s";
    public static final String STATEMENT_RANGE_EXCEEDED = "Statement date range cannot exceed %d months";
    public static final String STATEMENT_END_BEFORE_BEGIN = "End date cannot be before begin date";

    // AccountRepository log messages (Slf4j-style, {} placeholders)
    public static final String LOG_ACCOUNT_NUMBER_GENERATED = "Generated new {} account number {}";
    public static final String LOG_ACCOUNT_SAVED = "Saved account {}";
    public static final String LOG_ACCOUNT_UPDATED = "Updated account {}";
    public static final String LOG_WITHDRAWAL_INSUFFICIENT_FUNDS = "Withdrawal of {} rejected for account {}: insufficient funds (balance {})";
    public static final String LOG_WITHDRAWAL_ACCOUNT_NOT_FOUND = "Withdrawal failed: account {} not found";
    public static final String LOG_WITHDRAWAL_SUCCESS = "Withdrew {} from {} account {}";
    public static final String LOG_DEPOSIT_ACCOUNT_NOT_FOUND = "Deposit failed: account {} not found";
    public static final String LOG_DEPOSIT_SUCCESS = "Deposited {} into {} account {}";
    public static final String LOG_WITHDRAWAL_BELOW_MINIMUM_BALANCE = "Withdrawal of {} rejected for account {}: resulting balance {} would be below minimum {}";
    public static final String LOG_WITHDRAWAL_REJECTED_CLOSED = "Withdrawal rejected: account {} is closed";
    public static final String LOG_DEPOSIT_REJECTED_CLOSED = "Deposit rejected: account {} is closed";
    public static final String LOG_ACCOUNT_CLOSED = "Closed account {}";
    public static final String LOG_ACCOUNT_CLOSE_REJECTED_ALREADY_CLOSED = "Close rejected: account {} is already closed";
    public static final String LOG_ACCOUNT_CLOSE_ACCOUNT_NOT_FOUND = "Close failed: account {} not found";
    public static final String LOG_BULK_CLOSE_PROCESSING = "Bulk-closing {} accounts";
    public static final String LOG_BULK_CLOSE_ITEM_FAILED = "Bulk close skipped account {}: {}";
    public static final String LOG_BULK_CLOSE_COMPLETED = "Bulk close completed: {} closed, {} failed";

    // WithdrawalRepository log messages
    public static final String LOG_WITHDRAWAL_HISTORY_RECORDED = "Recorded withdrawal history entry for account {}: amount={}, status={}";

    // TransactionRepository log messages
    public static final String LOG_TRANSACTION_RECORDED = "Recorded {} transaction for account {}: amount={}, balanceAfter={}";

    // ClientAccountService log messages
    public static final String LOG_ACCOUNT_LOOKUP = "Looking up account {}";
    public static final String LOG_ACCOUNT_LOOKUP_FAILED = "Account lookup failed: {} not found";
    public static final String LOG_ACCOUNT_REGISTERED = "Registered new {} account {}";
    public static final String LOG_WITHDRAWAL_REJECTED_ACCOUNT_NUMBER = "Withdrawal rejected: missing/malformed account number";
    public static final String LOG_WITHDRAWAL_REJECTED_AMOUNT = "Withdrawal rejected for account {}: amount must be positive, got {}";
    public static final String LOG_WITHDRAWAL_REJECTED_PREFIX = "Withdrawal rejected: unrecognized account number prefix {}";
    public static final String LOG_WITHDRAWAL_REJECTED_TYPE_MISMATCH = "Withdrawal rejected for account {}: requested type {} does not match account type {}";
    public static final String LOG_WITHDRAWAL_PROCESSING = "Processing withdrawal of {} from {} account {}";
    public static final String LOG_DEPOSIT_REJECTED_ACCOUNT_NUMBER = "Deposit rejected: missing/malformed account number";
    public static final String LOG_DEPOSIT_REJECTED_AMOUNT = "Deposit rejected for account {}: amount must be positive, got {}";
    public static final String LOG_DEPOSIT_REJECTED_PREFIX = "Deposit rejected: unrecognized account number prefix {}";
    public static final String LOG_DEPOSIT_REJECTED_TYPE_MISMATCH = "Deposit rejected for account {}: requested type {} does not match account type {}";
    public static final String LOG_DEPOSIT_REJECTED_TYPE_INVALID = "Deposit rejected for account {}: invalid deposit type {}";
    public static final String LOG_DEPOSIT_PROCESSING = "Processing {} deposit of {} into {} account {}";
    public static final String LOG_DEPOSIT_REJECTED_MAX_CASH = "Deposit rejected for account {}: cash amount {} exceeds maximum {}";
    public static final String LOG_REGISTRATION_REJECTED_AGE = "Registration rejected: age {} is below minimum {}";
    public static final String LOG_ACCOUNT_SEARCH_REJECTED_DATE_RANGE = "Account search rejected: invalid date range - {}";
    public static final String LOG_ACCOUNT_SEARCH_REJECTED_MONTHS = "Account search rejected: invalid months value {}";
    public static final String LOG_ACCOUNT_SEARCH_DEFAULT_LOOKBACK_APPLIED = "No createdFrom/createdTo given; defaulting to a {}-month lookback window ({} to {})";
    public static final String LOG_ACCOUNT_SEARCH = "Searching accounts: accountNumber={}, status={}, createdFrom={}, createdTo={}, closedFrom={}, closedTo={}, page={}";
    public static final String LOG_WITHDRAWAL_REJECTED_SUSPENDED = "Withdrawal rejected: account {} is suspended";
    public static final String LOG_DEPOSIT_REJECTED_SUSPENDED = "Deposit rejected: account {} is suspended";
    public static final String LOG_ACCOUNT_SUSPEND_ACCOUNT_NOT_FOUND = "Suspend failed: account {} not found";
    public static final String LOG_ACCOUNT_SUSPEND_REJECTED = "Suspend rejected for account {}: {}";
    public static final String LOG_ACCOUNT_SUSPENDED = "Suspended account {} from {} until {}";
    public static final String LOG_SUSPENSION_UPDATED = "Updated suspension of account {}: end={}, notes changed={}";
    public static final String LOG_ACCOUNT_REACTIVATED = "Reactivated account {}";
    public static final String LOG_SUSPENSIONS_EXPIRED = "Reactivated {} account(s) whose suspension ended";
    public static final String LOG_HANDLER_ACCOUNT_SUSPENDED = "Account suspended: {}";
    public static final String LOG_PORTAL_HOME = "Portal home requested: state={}";
    public static final String LOG_PORTAL_OVERVIEW = "Portal overview requested: account={}, days={}";
    public static final String LOG_PORTAL_STATEMENT = "Portal statement requested: account={}, {} to {}";
    public static final String LOG_PORTAL_ACCOUNT_OPENED = "Portal opened an account: state={}";
    public static final String LOG_LOCATION_SEARCH = "Searching bank locations: type={}, city={}, state={}, zip={}, service={}, page={}";

    // BankStatementService log messages
    public static final String LOG_STATEMENT_REJECTED_RANGE = "Statement rejected for account {}: range {} to {} exceeds maximum {} months";
    public static final String LOG_STATEMENT_REJECTED_DATE_ORDER = "Statement rejected for account {}: end date {} is before begin date {}";
    public static final String LOG_STATEMENT_GENERATED = "Generated statement for account {}: {} transactions between {} and {}";

    // EmployeeCredentialService log messages
    public static final String LOG_LOGIN_CREATED = "Created {} login for {}";
    public static final String LOG_LOGIN_VERIFIED = "{} {} login verified";
    public static final String LOG_LOGIN_UNKNOWN_USER = "{} login attempt for an unknown username";
    public static final String LOG_LOGIN_FAILED = "Failed {} login for id {} (failed attempts: {})";
    public static final String LOG_LOGIN_LOCKED = "{} id {} locked until {} after {} failed attempts";
    public static final String LOG_LOGIN_REFUSED_LOCKED = "{} login refused for id {}: locked until {}";

    public static final String LOG_LOGIN_STATUS_CHANGED = "{} id {} login status set to {} (reason: {})";
    public static final String LOG_LOGIN_UNLOCKED = "{} id {} lock expired; login is ACTIVE again";

    // StaffAccountService log messages
    public static final String LOG_STAFF_ACTION = "Employee {} ({}) performed {} on account {}";

    // BankingExceptionHandler log messages
    public static final String LOG_HANDLER_ACCOUNT_NOT_FOUND = "Account not found: {}";
    public static final String LOG_HANDLER_LOCATION_NOT_FOUND = "Bank location not found: {}";
    public static final String LOG_HANDLER_EMPLOYEE_NOT_FOUND = "Employee not found: {}";
    public static final String LOG_HANDLER_EMPLOYEE_NOT_AUTHORIZED = "Employee not authorized: {}";
    public static final String LOG_HANDLER_INVALID_CREDENTIALS = "Invalid credentials: {}";
    public static final String LOG_HANDLER_EMPLOYEE_LOCKED = "Employee login locked: {}";
    public static final String LOG_HANDLER_CUSTOMER_NOT_FOUND = "Customer not found: {}";
    public static final String LOG_HANDLER_LOGIN_NOT_ACTIVE = "Login not active: {}";
    public static final String LOG_HANDLER_ACCOUNT_CLOSED = "Account closed: {}";
    public static final String LOG_HANDLER_INSUFFICIENT_FUNDS = "Insufficient funds: {}";
    public static final String LOG_HANDLER_INVALID_REQUEST = "Invalid banking request: {}";
    public static final String LOG_HANDLER_AGE_VIOLATION = "Age constraint violated: {}";
    public static final String LOG_HANDLER_MIN_BALANCE_VIOLATION = "Minimum balance constraint violated: {}";
    public static final String LOG_HANDLER_MAX_DEPOSIT_VIOLATION = "Maximum cash deposit constraint violated: {}";
    public static final String LOG_HANDLER_STATEMENT_RANGE_VIOLATION = "Statement range constraint violated: {}";

    // NotificationService log messages
    public static final String LOG_EMAIL_SENT = "Email sent to {} on thread: {}";
    public static final String LOG_EMAIL_DISABLED = "Email notifications disabled; skipping send to {}";
    public static final String LOG_EMAIL_INTERRUPTED = "Email send to {} interrupted";
    public static final String LOG_SMS_SENT = "SMS sent to {} on thread: {}";
    public static final String LOG_SMS_DISABLED = "SMS notifications disabled; skipping send to {}";
    public static final String LOG_SMS_DAILY_LIMIT_REACHED = "SMS daily limit of {} reached; skipping send to {}";
    public static final String LOG_SMS_INTERRUPTED = "SMS send to {} interrupted";
    public static final String LOG_REPORT_INTERRUPTED = "Report generation interrupted";
    public static final String LOG_REPORT_GENERATED = "Report generated successfully";

    // PaymentService log messages
    public static final String LOG_PAYMENT_PROCESSING = "Processing payment via BankClient";
    public static final String LOG_PAYMENT_FALLBACK_TRIGGERED = "Payment circuit breaker fallback triggered: {}";

    // CustomerService log messages
    public static final String LOG_CUSTOMER_SAMPLE_RETURNED = "Returning sample customer record";

    // Banking API gateway / rate limiter messages
    public static final String RATE_LIMIT_CUSTOMER_HEADER_REQUIRED = "Missing required customer identifier header: %s";
    public static final String RATE_LIMIT_DAILY_LIMIT_EXCEEDED = "Daily request limit exceeded for customer %s: max %d requests per day";
    public static final String LOG_RATE_LIMIT_HEADER_MISSING = "Rate limit rejected request to {}: missing header {}";
    public static final String LOG_RATE_LIMIT_EXCEEDED = "Rate limit exceeded for customer {} on {}: daily limit of {} requests reached";

    public static final String LOG_REQUEST_RECEIVED = "Request {}";

    // Shared domain/request validation messages (Address, AccountRegistrationRequest,
    // WithdrawalRequest, WithdrawalForm, DepositForm)
    public static final String VALIDATION_STREET_REQUIRED = "Street address is required";
    public static final String VALIDATION_CITY_REQUIRED = "City is required";
    public static final String VALIDATION_CITY_INVALID_CHARS = "Invalid characters in city name";
    public static final String VALIDATION_STATE_REQUIRED = "State is required";
    public static final String VALIDATION_STATE_LENGTH = "State must be exactly 2 characters (e.g., TX)";
    public static final String VALIDATION_ZIP_REQUIRED = "Zip code is required";

    // AccountLookupRequest-specific validation messages
    public static final String VALIDATION_USERNAME_REQUIRED = "username is required";
    public static final String VALIDATION_PASSWORD_REQUIRED = "password is required";
    public static final String VALIDATION_PHONE_REQUIRED = "Phone number is required";
    public static final String VALIDATION_PHONE_FORMAT = "Phone number must be in the format 512-555-0101";
    public static final String VALIDATION_SUSPENSION_NOTES_REQUIRED = "Suspension notes are required";
    public static final String VALIDATION_SUSPENSION_NOTES_MAX_LENGTH = "Suspension notes must be at most 500 characters";
    public static final String VALIDATION_ACCOUNT_NUMBER_REQUIRED = "Account number is required";

    // BulkCloseAccountsRequest-specific validation messages
    public static final String VALIDATION_ACCOUNT_NUMBERS_REQUIRED = "At least one account number is required";

    // AccountRegistrationRequest-specific validation messages
    public static final String VALIDATION_FIRST_NAME_REQUIRED = "First name is required";
    public static final String VALIDATION_FIRST_NAME_MAX_LENGTH = "First name cannot exceed 50 characters";
    public static final String VALIDATION_LAST_NAME_REQUIRED = "Last name is required";
    public static final String VALIDATION_LAST_NAME_MAX_LENGTH = "Last name cannot exceed 50 characters";
    public static final String VALIDATION_DOB_REQUIRED = "Date of birth is required";
    public static final String VALIDATION_ADDRESS_LINE1_REQUIRED = "Address line1 is required";
    public static final String VALIDATION_ACCOUNT_TYPE_REQUIRED = "Account type is required";
    public static final String VALIDATION_DOB_YEAR_MIN = "Must be 1940 or later, and minimum age requirement is 18";

    // Shared zip-format validation message (Address, AccountRegistrationRequest, DepositForm).
    // Previously fragmented as VALIDATION_ZIP_NON_NEGATIVE ("Non negative numbers" - didn't
    // even describe the constraint) and VALIDATION_DEPOSIT_ZIP_PATTERN; consolidated here.
    public static final String VALIDATION_ZIP_FORMAT = "Zip code must be exactly 5 digits";

    // DepositForm-specific validation messages
    public static final String VALIDATION_DEPOSIT_ADDRESS_LINE1_INVALID_CHARS = "Invalid characters in address line1";
    public static final String VALIDATION_DEPOSIT_TYPE_REQUIRED = "Deposit type is required";

    // Customer-specific validation messages
    public static final String VALIDATION_DOB_YEAR_1940 = "Date of birth must be from year 1940 onwards";

    // Shared "letters only" name-pattern message (Customer, DepositForm, WithdrawalRequest,
    // WithdrawalForm). Previously fragmented into five near-identical constants that only
    // differed by inconsistent comma/spacing typos; consolidated into one.
    public static final String VALIDATION_NAME_LETTERS_ONLY = "Name must contain only letters, no spaces or special characters";

    // Shared state-format validation message (Address, AccountRegistrationRequest,
    // WithdrawalRequest, DepositForm)
    public static final String VALIDATION_STATE_UPPERCASE = "State must be uppercase letters only";
}

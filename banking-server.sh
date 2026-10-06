#!/bin/bash

################################################################################
# Banking Services Server Control Script
#
# Purpose: Start, stop, and manage the Banking Services Spring Boot application
#
# Usage:
#   ./banking-server.sh start     - Start the server
#   ./banking-server.sh stop      - Stop the running server
#   ./banking-server.sh status    - Check if server is running
#   ./banking-server.sh restart   - Restart the server
#
# Requirements:
#   - Java 25 installed
#   - Maven installed
#   - MySQL running on localhost:3306 with db_example database
#   - Running from the bankingservices project directory
#
################################################################################

set -euo pipefail

# Configuration
PROJECT_DIR="/Users/mveluru/IdeaProjects/gitprojects/bankingservices"
PID_FILE="${PROJECT_DIR}/.server.pid"
LOG_DIR="${PROJECT_DIR}/logs"
LOG_FILE="${LOG_DIR}/banking-server.log"
SERVER_PORT=8081
SERVER_URL="http://localhost:${SERVER_PORT}/brite"
STARTUP_TIMEOUT=60  # seconds to wait for server to start

# Create logs directory if it doesn't exist
mkdir -p "${LOG_DIR}"

################################################################################
# Helper Functions
################################################################################

# Log message to both console and log file
log_info() {
    local message="$1"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] INFO: $message" | tee -a "${LOG_FILE}"
}

# Log error message and exit
log_error() {
    local message="$1"
    local exit_code="${2:-1}"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $message" | tee -a "${LOG_FILE}" >&2
    exit "${exit_code}"
}

# Log warning message
log_warn() {
    local message="$1"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] WARN: $message" | tee -a "${LOG_FILE}"
}

# Check if server is currently running
is_server_running() {
    if [[ ! -f "${PID_FILE}" ]]; then
        return 1
    fi

    local pid=$(cat "${PID_FILE}" 2>/dev/null || echo "")
    if [[ -z "${pid}" ]]; then
        return 1
    fi

    # Check if process with that PID exists
    if kill -0 "${pid}" 2>/dev/null; then
        return 0
    else
        return 1
    fi
}

# Check if MySQL is accessible
check_mysql() {
    log_info "Checking MySQL connectivity..."
    if ! command -v mysql &> /dev/null; then
        log_warn "MySQL client not found in PATH, skipping connectivity check"
        return 0
    fi

    if mysql -h localhost -u root -e "SELECT 1 FROM db_example.accounts LIMIT 1;" &>/dev/null; then
        log_info "MySQL connection successful"
        return 0
    else
        log_error "Cannot connect to MySQL. Ensure MySQL is running and db_example exists." 1
    fi
}

# Validate Java 25 is available
check_java() {
    log_info "Checking Java 25 availability..."

    # Try to find Java 25
    local java_home=$(/usr/libexec/java_home -v 25 2>/dev/null || echo "")

    if [[ -z "${java_home}" ]]; then
        log_error "Java 25 not found. Please install Java 25 or ensure it's available via /usr/libexec/java_home" 1
    fi

    log_info "Found Java 25 at: ${java_home}"
    return 0
}

################################################################################
# Main Commands
################################################################################

# Start the server
start_server() {
    log_info "=========================================="
    log_info "Starting Banking Services Server"
    log_info "=========================================="

    # Check prerequisites
    check_java
    check_mysql

    # Check if already running
    if is_server_running; then
        local pid=$(cat "${PID_FILE}")
        log_warn "Server is already running with PID ${pid}"
        log_info "Access the server at: ${SERVER_URL}"
        return 0
    fi

    # Check if project directory exists
    if [[ ! -d "${PROJECT_DIR}" ]]; then
        log_error "Project directory not found: ${PROJECT_DIR}" 1
    fi

    cd "${PROJECT_DIR}"
    log_info "Changed to project directory: ${PROJECT_DIR}"

    # Set Java 25 environment
    export JAVA_HOME=$(/usr/libexec/java_home -v 25)
    log_info "Set JAVA_HOME to: ${JAVA_HOME}"

    # Verify Maven
    if ! command -v mvn &> /dev/null; then
        log_error "Maven not found in PATH. Please install Maven." 1
    fi
    log_info "Maven found: $(mvn --version | head -n 1)"

    # Start the server in background
    log_info "Starting Spring Boot application..."
    (mvn spring-boot:run >> "${LOG_FILE}" 2>&1) &
    local pid=$!

    # Save PID
    echo "${pid}" > "${PID_FILE}"
    log_info "Server process started with PID: ${pid}"
    log_info "Logs will be written to: ${LOG_FILE}"

    # Wait for server to start
    log_info "Waiting for server to start (timeout: ${STARTUP_TIMEOUT}s)..."
    local elapsed=0
    local started=false

    while [[ ${elapsed} -lt ${STARTUP_TIMEOUT} ]]; do
        if is_server_running; then
            # Give it a moment to fully initialize
            sleep 2
            if curl -s "${SERVER_URL}/swagger-ui/index.html" > /dev/null 2>&1; then
                started=true
                break
            fi
        fi

        sleep 1
        elapsed=$((elapsed + 1))

        # Show progress every 10 seconds
        if [[ $((elapsed % 10)) -eq 0 ]]; then
            log_info "Still waiting... (${elapsed}/${STARTUP_TIMEOUT}s)"
        fi
    done

    if [[ "${started}" == true ]]; then
        log_info "=========================================="
        log_info "✓ Server started successfully!"
        log_info "=========================================="
        log_info "Server URL: ${SERVER_URL}"
        log_info "Swagger UI: ${SERVER_URL}/swagger-ui/index.html"
        log_info "API Docs: ${SERVER_URL}/swagger-ui.html"
        log_info "Process ID: ${pid}"
        log_info "Log file: ${LOG_FILE}"
        log_info "=========================================="
        return 0
    else
        # Check if process died
        if ! is_server_running; then
            log_error "Server failed to start. Check log file: ${LOG_FILE}" 1
        else
            log_warn "Server started but didn't respond to health check within ${STARTUP_TIMEOUT}s"
            log_info "Process is running (PID: ${pid}). Check logs: ${LOG_FILE}"
            return 0
        fi
    fi
}

# Stop the server
stop_server() {
    log_info "=========================================="
    log_info "Stopping Banking Services Server"
    log_info "=========================================="

    if [[ ! -f "${PID_FILE}" ]]; then
        log_warn "No PID file found. Server may not be running."
        return 0
    fi

    local pid=$(cat "${PID_FILE}")

    if ! is_server_running; then
        log_warn "Server is not running (PID ${pid} not found)"
        rm -f "${PID_FILE}"
        return 0
    fi

    log_info "Sending SIGTERM to process ${pid}..."
    kill "${pid}" || log_error "Failed to kill process ${pid}" 1

    # Wait for graceful shutdown
    local elapsed=0
    local shutdown_timeout=15

    log_info "Waiting for graceful shutdown (timeout: ${shutdown_timeout}s)..."
    while [[ ${elapsed} -lt ${shutdown_timeout} ]]; do
        if ! is_server_running; then
            log_info "Server stopped gracefully"
            rm -f "${PID_FILE}"
            log_info "=========================================="
            log_info "✓ Server stopped successfully"
            log_info "=========================================="
            return 0
        fi

        sleep 1
        elapsed=$((elapsed + 1))
    done

    # Force kill if still running
    log_warn "Graceful shutdown timeout, sending SIGKILL..."
    kill -9 "${pid}" 2>/dev/null || true
    sleep 1

    if ! is_server_running; then
        rm -f "${PID_FILE}"
        log_info "Server terminated forcefully"
        log_info "=========================================="
        log_info "✓ Server stopped"
        log_info "=========================================="
        return 0
    else
        log_error "Failed to stop server (PID ${pid})" 1
    fi
}

# Check server status
check_status() {
    log_info "=========================================="
    log_info "Banking Services Server Status"
    log_info "=========================================="

    if is_server_running; then
        local pid=$(cat "${PID_FILE}")
        log_info "✓ Server is RUNNING"
        log_info "Process ID: ${pid}"
        log_info "Server URL: ${SERVER_URL}"

        # Try to get health status
        if command -v curl &> /dev/null; then
            if curl -s "${SERVER_URL}/actuator/health/liveness" > /dev/null 2>&1; then
                log_info "Health check: HEALTHY"
            else
                log_warn "Health check: UNAVAILABLE or UNHEALTHY"
            fi
        fi
    else
        log_info "✗ Server is STOPPED"
        if [[ -f "${PID_FILE}" ]]; then
            local pid=$(cat "${PID_FILE}")
            log_info "Last known PID: ${pid}"
            rm -f "${PID_FILE}"
        fi
    fi

    log_info "Log file: ${LOG_FILE}"
    log_info "=========================================="
}

# Restart the server
restart_server() {
    log_info "=========================================="
    log_info "Restarting Banking Services Server"
    log_info "=========================================="

    if is_server_running; then
        stop_server
        sleep 2
    fi

    start_server
}

# Show tail of logs
show_logs() {
    if [[ ! -f "${LOG_FILE}" ]]; then
        log_error "Log file not found: ${LOG_FILE}" 1
    fi

    log_info "Showing last 100 lines of log file:"
    tail -n 100 "${LOG_FILE}"
}

################################################################################
# Main Script
################################################################################

# Parse command line arguments
COMMAND="${1:-}"

case "${COMMAND}" in
    start)
        start_server
        ;;
    stop)
        stop_server
        ;;
    status)
        check_status
        ;;
    restart)
        restart_server
        ;;
    logs)
        show_logs
        ;;
    *)
        cat << EOF
Banking Services Server Control Script

Usage: $0 <command>

Commands:
    start       - Start the Banking Services server
    stop        - Stop the running server
    status      - Check server status
    restart     - Restart the server (stop then start)
    logs        - Show the last 100 lines of the log file
    help        - Show this help message

Examples:
    $0 start        # Start the server
    $0 stop         # Stop the server
    $0 status       # Check if server is running
    $0 restart      # Restart the server
    $0 logs         # View recent logs

Configuration:
    Project Dir:    ${PROJECT_DIR}
    PID File:       ${PID_FILE}
    Log File:       ${LOG_FILE}
    Server Port:    ${SERVER_PORT}
    Server URL:     ${SERVER_URL}

Prerequisites:
    - Java 25 installed
    - Maven installed
    - MySQL running on localhost:3306
    - Database: db_example

Log File Location:
    ${LOG_FILE}

For more information, check the script header or README.md

EOF
        ;;
esac

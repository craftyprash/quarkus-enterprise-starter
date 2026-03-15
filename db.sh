#!/usr/bin/env bash
set -euo pipefail

# ── Config ──────────────────────────────────────────────────────────
CHANGELOG_DIR="src/main/resources/db/changelog"
MASTER_FILE="$CHANGELOG_DIR/db.changelog-master.yaml"
CHANGESET_AUTHOR="starter"

# DB connection — reads from .env, falls back to defaults
if [[ -f .env ]]; then
    # shellcheck disable=SC1091
    source .env
fi
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-starter}"
DB_USER="${QUARKUS_DATASOURCE_USERNAME:-postgres}"
DB_PASS="${QUARKUS_DATASOURCE_PASSWORD:-postgres}"
DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"

# ── Helpers ─────────────────────────────────────────────────────────
red()   { printf '\033[0;31m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
bold()  { printf '\033[1m%s\033[0m\n' "$*"; }

next_number() {
    local last
    last=$(ls "$CHANGELOG_DIR"/*.sql 2>/dev/null | sed 's/.*\///' | sort -n | tail -1 | grep -oE '^[0-9]+' || echo "0")
    printf "%03d" $((last + 1))
}

psql_cmd() {
    PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" "$@"
}

usage() {
    cat <<EOF
$(bold "Usage: ./db.sh <command> [args]")

$(bold "Commands:")
  new <name>           Create a new changeset SQL file and register in master YAML
  new <name> --audit   Same as above, but includes _AUD table boilerplate
  status               Show pending changesets (not yet applied)
  validate             Validate changelog syntax without applying
  rollback <n>         Rollback the last N changesets
  history              Show applied changesets
  diff                 Show what Hibernate would generate vs current DB schema
  psql                 Open psql shell to the dev database

$(bold "Examples:")
  ./db.sh new create-refund
  ./db.sh new create-settlement --audit
  ./db.sh status
  ./db.sh rollback 1
EOF
}

# ── Commands ────────────────────────────────────────────────────────

cmd_new() {
    local name="${1:-}"
    local audit=false

    if [[ -z "$name" ]]; then
        red "Error: missing changeset name"
        echo "Usage: ./db.sh new <name> [--audit]"
        exit 1
    fi

    if [[ "${2:-}" == "--audit" ]]; then
        audit=true
    fi

    local num
    num=$(next_number)
    local filename="${num}-${name}.sql"
    local filepath="${CHANGELOG_DIR}/${filename}"
    local changeset_id="${num}-${name}"

    # derive table name from changeset name: "create-refund" → "refund"
    local table_name
    table_name=$(echo "$name" | sed 's/^create-//' | sed 's/^add-//' | tr '-' '_')

    # generate SQL file — CREATE TABLE boilerplate only for "create-*" names
    if [[ "$name" == create-* ]]; then
        cat > "$filepath" <<SQL
--liquibase formatted sql

--changeset ${CHANGESET_AUTHOR}:${changeset_id}
CREATE TABLE ${table_name} (
    id          BIGSERIAL PRIMARY KEY,
    -- TODO: add columns
    status      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
SQL

        if [[ "$audit" == true ]]; then
            cat >> "$filepath" <<SQL

--changeset ${CHANGESET_AUTHOR}:${changeset_id}-aud
CREATE TABLE ${table_name}_aud (
    id          BIGINT       NOT NULL,
    rev         INTEGER      NOT NULL REFERENCES revinfo(rev),
    revtype     SMALLINT,
    -- TODO: mirror columns from main table (nullable, no defaults)
    status      VARCHAR(50),
    PRIMARY KEY (id, rev)
);
SQL
        fi
    else
        cat > "$filepath" <<SQL
--liquibase formatted sql

--changeset ${CHANGESET_AUTHOR}:${changeset_id}
-- TODO: add your SQL here
SQL
    fi

    # register in master YAML
    echo "  - include:" >> "$MASTER_FILE"
    echo "      file: db/changelog/${filename}" >> "$MASTER_FILE"

    green "Created: ${filepath}"
    if [[ "$audit" == true ]]; then
        green "  → includes _AUD table boilerplate"
    fi
    green "  → registered in ${MASTER_FILE}"
    echo ""
    bold "Next: edit the file, add your columns, then run ./db.sh validate"
}

cmd_status() {
    bold "Pending changesets:"
    echo ""
    eval "$(mise activate bash 2>/dev/null)" 2>/dev/null || true
    mvn liquibase:status \
        -Dliquibase.changeLogFile="$MASTER_FILE" \
        -Dliquibase.url="$DB_URL" \
        -Dliquibase.username="$DB_USER" \
        -Dliquibase.password="$DB_PASS" \
        -q 2>&1 | grep -v "^\[" || true
}

cmd_validate() {
    bold "Validating changelog..."
    echo ""
    eval "$(mise activate bash 2>/dev/null)" 2>/dev/null || true
    mvn liquibase:validate \
        -Dliquibase.changeLogFile="$MASTER_FILE" \
        -Dliquibase.url="$DB_URL" \
        -Dliquibase.username="$DB_USER" \
        -Dliquibase.password="$DB_PASS" \
        -q 2>&1 | grep -v "^\[" || true
    green "Validation complete"
}

cmd_rollback() {
    local count="${1:-}"
    if [[ -z "$count" ]]; then
        red "Error: specify number of changesets to rollback"
        echo "Usage: ./db.sh rollback <n>"
        exit 1
    fi

    bold "Rolling back ${count} changeset(s)..."
    echo ""
    eval "$(mise activate bash 2>/dev/null)" 2>/dev/null || true
    mvn liquibase:rollback \
        -Dliquibase.changeLogFile="$MASTER_FILE" \
        -Dliquibase.url="$DB_URL" \
        -Dliquibase.username="$DB_USER" \
        -Dliquibase.password="$DB_PASS" \
        -Dliquibase.rollbackCount="$count" \
        -q 2>&1 | grep -v "^\[" || true
    green "Rollback complete"
}

cmd_history() {
    bold "Applied changesets:"
    echo ""
    psql_cmd -c "SELECT id, author, filename, dateexecuted, orderexecuted FROM databasechangelog ORDER BY orderexecuted;" 2>/dev/null \
        || red "Could not connect to database. Is PostgreSQL running?"
}

cmd_diff() {
    bold "Generating schema diff (Hibernate vs DB)..."
    echo ""
    eval "$(mise activate bash 2>/dev/null)" 2>/dev/null || true
    mvn quarkus:dev -Dquarkus.hibernate-orm.database.generation=validate -Dquarkus.liquibase.migrate-at-start=false 2>&1 \
        | grep -iE "(schema|missing|unexpected|alter|create|drop|error)" || green "Schema is in sync"
}

cmd_psql() {
    bold "Connecting to ${DB_NAME}@${DB_HOST}:${DB_PORT}..."
    psql_cmd
}

# ── Main ────────────────────────────────────────────────────────────
case "${1:-}" in
    new)      shift; cmd_new "$@" ;;
    status)   cmd_status ;;
    validate) cmd_validate ;;
    rollback) shift; cmd_rollback "$@" ;;
    history)  cmd_history ;;
    diff)     cmd_diff ;;
    psql)     cmd_psql ;;
    *)        usage ;;
esac

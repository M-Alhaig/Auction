#!/bin/bash
set -e

# This script creates multiple PostgreSQL databases
# It reads from POSTGRES_MULTIPLE_DATABASES environment variable

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE IF NOT EXISTS user_db;
    CREATE DATABASE IF NOT EXISTS item_db;
    CREATE DATABASE IF NOT EXISTS bidding_db;
    CREATE DATABASE IF NOT EXISTS notification_db;
EOSQL

echo "Multiple databases created successfully!"

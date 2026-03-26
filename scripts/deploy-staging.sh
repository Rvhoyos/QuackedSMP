#!/bin/bash
set -e
# Arguments
BUILD_DIR=$1

STAGING_PATH="/home/ubuntu/mcserver/staging"
SERVICE_NAME="mc-staging"

echo "Starting Staging Deployment..."
echo "Destination: $STAGING_PATH"



# 1. Stop Staging Service (if running)
echo "Stopping service $SERVICE_NAME..."
sudo systemctl stop "$SERVICE_NAME" || true

# 2. Install New Jar
# Find the new jar name (robust finding)
NEW_JAR=$(find "$BUILD_DIR" -name "*.jar" | grep -v 'sources' | grep -v 'javadoc' | head -n 1)
JAR_NAME=$(basename "$NEW_JAR")

echo "Installing $JAR_NAME..."

# Remove OLD versions of this specific mod to prevent duplicates
# Strictly match "QuackedSMP" or "quackedsmp" to avoid deleting "QuackedMod"
sudo find "$STAGING_PATH/mods" -name "QuackedSMP-*.jar" -delete
sudo find "$STAGING_PATH/mods" -name "quackedsmp-*.jar" -delete


# Copy new jar
sudo cp "$NEW_JAR" "$STAGING_PATH/mods/"
OWNER_GROUP=$(stat -c '%U:%G' "$STAGING_PATH/mods")
sudo chown "$OWNER_GROUP" "$STAGING_PATH/mods/$JAR_NAME"


# 5. Start Staging Service

echo "Starting service $SERVICE_NAME..."
sudo systemctl start "$SERVICE_NAME"

echo "Staging Deployment Complete!"

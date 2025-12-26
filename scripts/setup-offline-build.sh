#!/bin/bash
set -e

M2_REPO="$HOME/.m2/repository"
GOOGLE_REPO="https://dl.google.com/dl/android/maven2"
MAVEN_REPO="https://repo.maven.apache.org/maven2"
GRADLE_PLUGINS="https://plugins.gradle.org/m2"

log() { echo "[setup] $1"; }

download_artifact() {
    local group=$1 artifact=$2 version=$3 repo=$4
    local group_path="${group//./\/}"
    local base_url="$repo/$group_path/$artifact/$version"
    local target_dir="$M2_REPO/$group_path/$artifact/$version"
    mkdir -p "$target_dir"
    for ext in pom jar module; do
        local file="$artifact-$version.$ext"
        [ -f "$target_dir/$file" ] || curl -sfL -o "$target_dir/$file" "$base_url/$file" 2>/dev/null || true
    done
}

log "Downloading Gradle plugins..."
download_artifact com.android.application com.android.application.gradle.plugin 8.7.2 $GOOGLE_REPO
download_artifact com.android.tools.build gradle 8.7.2 $GOOGLE_REPO
download_artifact org.jetbrains.kotlin.android org.jetbrains.kotlin.android.gradle.plugin 2.0.21 $GRADLE_PLUGINS
download_artifact org.jetbrains.kotlin kotlin-gradle-plugin 2.0.21 $MAVEN_REPO
log "Done"

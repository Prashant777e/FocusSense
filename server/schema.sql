-- ==============================================================================
-- FocusSense PostgreSQL / Supabase Complete Database Schema
-- Run this directly in your Supabase SQL Editor:
-- https://supabase.com/dashboard/project/_/sql
-- ==============================================================================

-- 1. Family Groups Table
CREATE TABLE IF NOT EXISTS family_groups (
    group_id VARCHAR(64) PRIMARY KEY,
    family_name VARCHAR(128) NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

-- 2. Users Table (Parents and Children)
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) REFERENCES family_groups(group_id) ON DELETE CASCADE,
    email VARCHAR(128) UNIQUE NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('parent', 'child')),
    name VARCHAR(128) NOT NULL,
    pin VARCHAR(8) DEFAULT '1234',
    avatar VARCHAR(64) DEFAULT 'default'
);

-- 3. Hardware Devices Table
CREATE TABLE IF NOT EXISTS devices (
    device_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    device_name VARCHAR(128) NOT NULL,
    token TEXT, -- FCM Push Notification Registration Token
    battery_percent INT DEFAULT 100,
    is_online BOOLEAN DEFAULT true,
    last_active BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

-- 4. Activity Logs (Real-time Context & Threat Detection)
CREATE TABLE IF NOT EXISTS activity_logs (
    log_id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    package_name VARCHAR(128) NOT NULL,
    app_name VARCHAR(128) NOT NULL,
    content_title TEXT,
    extracted_text TEXT NOT NULL,
    is_flagged BOOLEAN DEFAULT false,
    threat_category VARCHAR(64), -- 'Stranger Risk', 'Cyberbullying', 'Academic Distraction', 'Self-Harm', etc.
    confidence_score REAL DEFAULT 0.0,
    ai_analysis_summary TEXT,
    recorded_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    is_synced BOOLEAN DEFAULT true,
    is_acknowledged BOOLEAN DEFAULT false
);

-- 5. Schedule Rules (Timetables & App Restrictions)
CREATE TABLE IF NOT EXISTS schedule_rules (
    rule_id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    rule_name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL, -- 'Homework', 'Study', 'Bedtime', 'Outdoor'
    start_time VARCHAR(10) NOT NULL, -- '15:30'
    end_time VARCHAR(10) NOT NULL,   -- '17:30'
    day_of_week VARCHAR(64) DEFAULT 'Mon,Tue,Wed,Thu,Fri,Sat,Sun',
    restricted_packages TEXT NOT NULL, -- comma separated package names
    is_active BOOLEAN DEFAULT true
);

-- 6. Location History (GPS Waypoints & Safe Zones)
CREATE TABLE IF NOT EXISTS location_history (
    loc_id VARCHAR(64) PRIMARY KEY,
    child_id VARCHAR(64) REFERENCES users(user_id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy REAL DEFAULT 5.0,
    location_name VARCHAR(128) DEFAULT 'Live GPS Fix',
    recorded_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    is_synced BOOLEAN DEFAULT true
);

-- ==============================================================================
-- High-Performance Indexes for Fast Querying
-- ==============================================================================
CREATE INDEX IF NOT EXISTS idx_activity_logs_child ON activity_logs(child_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_logs_flagged ON activity_logs(child_id, is_flagged);
CREATE INDEX IF NOT EXISTS idx_schedule_rules_child ON schedule_rules(child_id);
CREATE INDEX IF NOT EXISTS idx_location_child ON location_history(child_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(user_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_group ON users(group_id);

from rest_framework import serializers
from .models import Device, AppPolicy, ScheduleRule, AllowedYouTubeChannel

class DeviceSerializer(serializers.ModelSerializer):
    class Meta:
        model = Device
        fields = ['id', 'device_name', 'device_model', 'os_version', 'app_version', 'fcm_token', 'is_active', 'last_seen']
        read_only_fields = ['id', 'last_seen']


class AppPolicySerializer(serializers.ModelSerializer):
    class Meta:
        model = AppPolicy
        fields = ['id', 'package_name', 'app_name', 'is_blocked', 'daily_limit_minutes', 'version', 'updated_at']
        read_only_fields = ['id', 'version', 'updated_at']


class ScheduleRuleSerializer(serializers.ModelSerializer):
    class Meta:
        model = ScheduleRule
        fields = ['id', 'rule_name', 'rule_type', 'target_package', 'days_of_week', 'start_time', 'end_time', 'is_active', 'version', 'updated_at']
        read_only_fields = ['id', 'version', 'updated_at']


class AllowedYouTubeChannelSerializer(serializers.ModelSerializer):
    class Meta:
        model = AllowedYouTubeChannel
        fields = ['id', 'channel_id', 'channel_title', 'channel_handle', 'is_allowed', 'version', 'created_at']
        read_only_fields = ['id', 'version', 'created_at']

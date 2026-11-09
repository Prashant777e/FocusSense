from rest_framework import serializers
from .models import ActivityLog, LocationLog, SafetyAlert

class ActivityLogInputSerializer(serializers.Serializer):
    id = serializers.UUIDField(required=True)
    package_name = serializers.CharField(max_length=255)
    app_name = serializers.CharField(max_length=128)
    content_title = serializers.CharField(required=False, allow_blank=True, default='')
    threat_category = serializers.CharField(required=False, allow_blank=True, default='')
    confidence_score = serializers.FloatField(required=False, default=0.0)
    ai_analysis_summary = serializers.CharField(required=False, allow_blank=True, default='')
    is_flagged = serializers.BooleanField(required=False, default=False)
    timestamp = serializers.IntegerField(required=True)  # Millisecond epoch timestamp from Android


class LocationLogInputSerializer(serializers.Serializer):
    id = serializers.UUIDField(required=True)
    latitude = serializers.FloatField(required=True)
    longitude = serializers.FloatField(required=True)
    accuracy = serializers.FloatField(required=False, default=5.0)
    speed = serializers.FloatField(required=False, allow_null=True, default=None)
    timestamp = serializers.IntegerField(required=True)


class ActivityLogOutputSerializer(serializers.ModelSerializer):
    class Meta:
        model = ActivityLog
        fields = ['id', 'package_name', 'app_name', 'content_title', 'threat_category', 'confidence_score', 'ai_analysis_summary', 'is_flagged', 'timestamp']


class LocationLogOutputSerializer(serializers.ModelSerializer):
    class Meta:
        model = LocationLog
        fields = ['id', 'latitude', 'longitude', 'accuracy', 'speed', 'timestamp']


class SafetyAlertSerializer(serializers.ModelSerializer):
    log = ActivityLogOutputSerializer(read_only=True)
    class Meta:
        model = SafetyAlert
        fields = ['id', 'log', 'severity', 'status', 'notified_at']

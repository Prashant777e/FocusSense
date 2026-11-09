import uuid
from django.db import models
from django.conf import settings
from policies.models import Device

class ActivityLog(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)  # Matches client logId for idempotency
    device = models.ForeignKey(Device, on_delete=models.CASCADE, related_name='activity_logs')
    child = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='activity_logs')
    package_name = models.CharField(max_length=255, db_index=True)
    app_name = models.CharField(max_length=128)
    content_title = models.TextField(blank=True, default='')
    threat_category = models.CharField(max_length=64, blank=True, default='')
    confidence_score = models.FloatField(default=0.0)
    ai_analysis_summary = models.TextField(blank=True, default='')
    is_flagged = models.BooleanField(default=False, db_index=True)
    timestamp = models.DateTimeField(db_index=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'fs_activity_logs'
        indexes = [
            models.Index(fields=['child', 'timestamp']),
            models.Index(fields=['child', 'is_flagged']),
        ]
        verbose_name = 'Activity Log'
        verbose_name_plural = 'Activity Logs'

    def __str__(self):
        flag = "[FLAGGED]" if self.is_flagged else "[SAFE]"
        return f"{flag} {self.app_name} ({self.child.display_name})"


class LocationLog(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)  # Matches client locId
    device = models.ForeignKey(Device, on_delete=models.CASCADE, related_name='location_logs')
    child = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='location_logs')
    latitude = models.FloatField()
    longitude = models.FloatField()
    accuracy = models.FloatField(default=5.0)
    speed = models.FloatField(null=True, blank=True)
    timestamp = models.DateTimeField(db_index=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'fs_location_logs'
        indexes = [
            models.Index(fields=['child', 'timestamp']),
        ]
        verbose_name = 'Location Log'
        verbose_name_plural = 'Location Logs'

    def __str__(self):
        return f"GPS ({self.latitude}, {self.longitude}) - {self.child.display_name}"


class SafetyAlert(models.Model):
    SEVERITY_CHOICES = (
        ('MEDIUM', 'Medium'),
        ('HIGH', 'High'),
        ('CRITICAL', 'Critical'),
    )
    STATUS_CHOICES = (
        ('NEW', 'New'),
        ('ACKNOWLEDGED', 'Acknowledged'),
        ('RESOLVED', 'Resolved'),
    )

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    log = models.OneToOneField(ActivityLog, on_delete=models.CASCADE, related_name='alert')
    child = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='safety_alerts')
    severity = models.CharField(max_length=20, choices=SEVERITY_CHOICES, default='HIGH')
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='NEW')
    notified_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'fs_safety_alerts'
        verbose_name = 'Safety Alert'
        verbose_name_plural = 'Safety Alerts'

    def __str__(self):
        return f"Alert [{self.severity}] - {self.child.display_name} ({self.status})"

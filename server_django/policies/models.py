import uuid
from django.db import models
from django.conf import settings

class Device(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='devices')
    device_name = models.CharField(max_length=128)
    device_model = models.CharField(max_length=128, blank=True, default='')
    os_version = models.CharField(max_length=64, blank=True, default='')
    app_version = models.CharField(max_length=64, blank=True, default='')
    fcm_token = models.TextField(blank=True, default='')
    is_active = models.BooleanField(default=True)
    last_seen = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'fs_devices'
        verbose_name = 'Hardware Device'
        verbose_name_plural = 'Hardware Devices'

    def __str__(self):
        return f"{self.device_name} ({self.user.display_name})"


class AppPolicy(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    child = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='app_policies')
    package_name = models.CharField(max_length=255, db_index=True)
    app_name = models.CharField(max_length=128)
    is_blocked = models.BooleanField(default=False)
    daily_limit_minutes = models.PositiveIntegerField(default=0)
    version = models.PositiveIntegerField(default=1)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'fs_app_policies'
        unique_together = ('child', 'package_name')
        verbose_name = 'App Policy'
        verbose_name_plural = 'App Policies'

    def save(self, *args, **kwargs):
        if self.pk:
            self.version += 1
        super().save(*args, **kwargs)

    def __str__(self):
        status = "BLOCKED" if self.is_blocked else "ALLOWED"
        return f"{self.app_name} -> {status} (v{self.version})"


class ScheduleRule(models.Model):
    RULE_TYPES = (
        ('BEDTIME', 'Bedtime'),
        ('STUDY', 'Study / Focus'),
        ('APP_LOCK', 'App Restriction'),
    )

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    child = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='schedule_rules')
    rule_name = models.CharField(max_length=128)
    rule_type = models.CharField(max_length=32, choices=RULE_TYPES, default='STUDY')
    target_package = models.CharField(max_length=255, blank=True, null=True)
    days_of_week = models.JSONField(default=list)  # e.g., [1, 2, 3, 4, 5] for Mon-Fri
    start_time = models.TimeField()
    end_time = models.TimeField()
    is_active = models.BooleanField(default=True)
    version = models.PositiveIntegerField(default=1)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'fs_schedule_rules'
        verbose_name = 'Schedule Rule'
        verbose_name_plural = 'Schedule Rules'

    def save(self, *args, **kwargs):
        if self.pk:
            self.version += 1
        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.rule_name} ({self.start_time} - {self.end_time})"


class AllowedYouTubeChannel(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    child = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='allowed_youtube_channels')
    channel_id = models.CharField(max_length=128, db_index=True)
    channel_title = models.CharField(max_length=255)
    channel_handle = models.CharField(max_length=128, blank=True, default='')
    is_allowed = models.BooleanField(default=True)
    version = models.PositiveIntegerField(default=1)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'fs_allowed_youtube_channels'
        unique_together = ('child', 'channel_id')
        verbose_name = 'Allowed YouTube Channel'
        verbose_name_plural = 'Allowed YouTube Channels'

    def save(self, *args, **kwargs):
        if self.pk:
            self.version += 1
        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.channel_title} ({self.channel_handle})"

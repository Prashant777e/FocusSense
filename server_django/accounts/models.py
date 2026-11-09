import uuid
from django.db import models
from django.contrib.auth.models import AbstractBaseUser, PermissionsMixin, BaseUserManager
from django.utils import timezone

class Family(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    family_name = models.CharField(max_length=128)
    invite_code = models.CharField(max_length=16, unique=True, db_index=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'fs_family_groups'
        verbose_name = 'Family Group'
        verbose_name_plural = 'Family Groups'

    def __str__(self):
        return f"{self.family_name} ({self.invite_code})"


class CustomUserManager(BaseUserManager):
    def create_user(self, email=None, username=None, password=None, role='PARENT', **extra_fields):
        if not username:
            if email:
                username = email.lower()
            else:
                raise ValueError("An identifier (email or username) is required.")
        
        email = self.normalize_email(email) if email else None
        user = self.model(
            email=email,
            username=username.lower().strip(),
            role=role.upper(),
            **extra_fields
        )
        if password:
            user.set_password(password)
        else:
            user.set_unusable_password()
        user.save(using=self._db)
        return user

    def create_superuser(self, email=None, username=None, password=None, **extra_fields):
        extra_fields.setdefault('is_staff', True)
        extra_fields.setdefault('is_superuser', True)
        extra_fields.setdefault('role', 'PARENT')
        if not username:
            username = email or 'admin'
        return self.create_user(email=email, username=username, password=password, **extra_fields)


class CustomUser(AbstractBaseUser, PermissionsMixin):
    ROLE_CHOICES = (
        ('PARENT', 'Parent'),
        ('CHILD', 'Child'),
    )

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    family = models.ForeignKey(
        Family,
        on_delete=models.CASCADE,
        related_name='members',
        null=True,
        blank=True
    )
    email = models.EmailField(max_length=128, unique=True, null=True, blank=True, db_index=True)
    username = models.CharField(max_length=128, unique=True, db_index=True)
    role = models.CharField(max_length=16, choices=ROLE_CHOICES, default='PARENT', db_index=True)
    display_name = models.CharField(max_length=128)
    created_by = models.ForeignKey(
        'self',
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='created_children'
    )
    is_active = models.BooleanField(default=True)
    is_staff = models.BooleanField(default=False)
    date_joined = models.DateTimeField(default=timezone.now)

    objects = CustomUserManager()

    USERNAME_FIELD = 'username'
    REQUIRED_FIELDS = ['display_name']

    class Meta:
        db_table = 'fs_users'
        verbose_name = 'FocusSense User'
        verbose_name_plural = 'FocusSense Users'
        indexes = [
            models.Index(fields=['family', 'role']),
        ]

    def __str__(self):
        return f"{self.display_name} ({self.role}) - {self.username}"

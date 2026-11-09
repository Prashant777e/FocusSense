from django.urls import path
from rest_framework_simplejwt.views import TokenRefreshView
from .views import (
    HealthCheckView,
    ParentRegisterView,
    UnifiedLoginView,
    FamilyChildrenView,
    UserProfileView,
)

urlpatterns = [
    # Health check endpoint
    path('health/', HealthCheckView.as_view(), name='health-check'),

    # Authentication Endpoints
    path('auth/parent/register/', ParentRegisterView.as_view(), name='parent-register'),
    path('auth/login/', UnifiedLoginView.as_view(), name='unified-login'),
    path('auth/token/refresh/', TokenRefreshView.as_view(), name='token-refresh'),
    path('auth/me/', UserProfileView.as_view(), name='user-me'),

    # Family & Child Provisioning
    path('family/children/', FamilyChildrenView.as_view(), name='family-children'),
]

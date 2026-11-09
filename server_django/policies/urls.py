from django.urls import path
from .views import (
    DeviceEnrollView,
    ChildPolicySyncView,
    ParentAppPolicyUpdateView,
    ParentScheduleRuleCreateView,
    ParentYouTubeWhitelistView,
)

urlpatterns = [
    path('devices/enroll/', DeviceEnrollView.as_view(), name='device-enroll'),
    path('children/<uuid:child_id>/policies/', ChildPolicySyncView.as_view(), name='child-policies-sync'),
    path('children/<uuid:child_id>/policies/apps/', ParentAppPolicyUpdateView.as_view(), name='parent-app-policy-update'),
    path('children/<uuid:child_id>/schedules/', ParentScheduleRuleCreateView.as_view(), name='parent-schedule-create'),
    path('children/<uuid:child_id>/youtube/', ParentYouTubeWhitelistView.as_view(), name='parent-youtube-whitelist'),
]

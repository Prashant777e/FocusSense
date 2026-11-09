from django.urls import path
from .views import (
    BatchActivityUploadView,
    BatchLocationUploadView,
    ParentChildActivityFeedView,
    ParentChildLatestLocationView,
    ParentChildSafetyAlertsView,
)

urlpatterns = [
    path('telemetry/activities/', BatchActivityUploadView.as_view(), name='batch-activity-upload'),
    path('telemetry/locations/', BatchLocationUploadView.as_view(), name='batch-location-upload'),
    path('children/<uuid:child_id>/activity-feed/', ParentChildActivityFeedView.as_view(), name='child-activity-feed'),
    path('children/<uuid:child_id>/location-latest/', ParentChildLatestLocationView.as_view(), name='child-latest-location'),
    path('children/<uuid:child_id>/alerts/', ParentChildSafetyAlertsView.as_view(), name='child-safety-alerts'),
]

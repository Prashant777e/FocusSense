from datetime import datetime, timezone as dt_timezone
from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from accounts.models import CustomUser
from accounts.permissions import IsParent, IsChild
from policies.models import Device
from .models import ActivityLog, LocationLog, SafetyAlert
from .serializers import (
    ActivityLogInputSerializer,
    LocationLogInputSerializer,
    ActivityLogOutputSerializer,
    LocationLogOutputSerializer,
    SafetyAlertSerializer,
)

class BatchActivityUploadView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        device_id = request.data.get('device_id')
        logs_data = request.data.get('logs', [])

        if not device_id:
            return Response({'status': 'error', 'message': 'device_id is required'}, status=status.HTTP_400_BAD_REQUEST)

        try:
            device = Device.objects.get(id=device_id)
        except Device.DoesNotExist:
            return Response({'status': 'error', 'message': 'Device not found'}, status=status.HTTP_404_NOT_FOUND)

        child = request.user
        accepted_ids = []

        for item in logs_data:
            serializer = ActivityLogInputSerializer(data=item)
            if serializer.is_valid():
                data = serializer.validated_data
                log_id = data['id']
                # Convert millisecond epoch to datetime
                ts_dt = datetime.fromtimestamp(data['timestamp'] / 1000.0, tz=dt_timezone.utc)

                log, created = ActivityLog.objects.update_or_create(
                    id=log_id,
                    defaults={
                        'device': device,
                        'child': child,
                        'package_name': data['package_name'],
                        'app_name': data['app_name'],
                        'content_title': data.get('content_title', ''),
                        'threat_category': data.get('threat_category', ''),
                        'confidence_score': data.get('confidence_score', 0.0),
                        'ai_analysis_summary': data.get('ai_analysis_summary', ''),
                        'is_flagged': data.get('is_flagged', False),
                        'timestamp': ts_dt,
                    }
                )
                accepted_ids.append(str(log.id))

                # If flagged, automatically create or update SafetyAlert
                if log.is_flagged and not hasattr(log, 'alert'):
                    severity = 'CRITICAL' if log.confidence_score > 0.85 else 'HIGH'
                    SafetyAlert.objects.create(
                        log=log,
                        child=child,
                        severity=severity,
                        status='NEW'
                    )

        return Response({
            'status': 'success',
            'accepted_ids': accepted_ids,
            'synced_count': len(accepted_ids)
        }, status=status.HTTP_200_OK)


class BatchLocationUploadView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        device_id = request.data.get('device_id')
        locations_data = request.data.get('locations', [])

        if not device_id:
            return Response({'status': 'error', 'message': 'device_id is required'}, status=status.HTTP_400_BAD_REQUEST)

        try:
            device = Device.objects.get(id=device_id)
        except Device.DoesNotExist:
            return Response({'status': 'error', 'message': 'Device not found'}, status=status.HTTP_404_NOT_FOUND)

        child = request.user
        accepted_ids = []

        for item in locations_data:
            serializer = LocationLogInputSerializer(data=item)
            if serializer.is_valid():
                data = serializer.validated_data
                loc_id = data['id']
                ts_dt = datetime.fromtimestamp(data['timestamp'] / 1000.0, tz=dt_timezone.utc)

                loc, _ = LocationLog.objects.update_or_create(
                    id=loc_id,
                    defaults={
                        'device': device,
                        'child': child,
                        'latitude': data['latitude'],
                        'longitude': data['longitude'],
                        'accuracy': data.get('accuracy', 5.0),
                        'speed': data.get('speed', None),
                        'timestamp': ts_dt,
                    }
                )
                accepted_ids.append(str(loc.id))

        return Response({
            'status': 'success',
            'accepted_ids': accepted_ids,
            'synced_count': len(accepted_ids)
        }, status=status.HTTP_200_OK)


class ParentChildActivityFeedView(APIView):
    permission_classes = [IsAuthenticated, IsParent]

    def get(self, request, child_id):
        try:
            child = CustomUser.objects.get(id=child_id, family=request.user.family, role='CHILD')
        except CustomUser.DoesNotExist:
            return Response({'status': 'error', 'message': 'Child not found in family'}, status=status.HTTP_404_NOT_FOUND)

        flagged_only = request.query_params.get('flagged_only', 'false').lower() == 'true'
        logs = ActivityLog.objects.filter(child=child)
        if flagged_only:
            logs = logs.filter(is_flagged=True)
        logs = logs.order_by('-timestamp')[:100]

        serializer = ActivityLogOutputSerializer(logs, many=True)
        return Response({'status': 'success', 'results': serializer.data}, status=status.HTTP_200_OK)


class ParentChildLatestLocationView(APIView):
    permission_classes = [IsAuthenticated, IsParent]

    def get(self, request, child_id):
        try:
            child = CustomUser.objects.get(id=child_id, family=request.user.family, role='CHILD')
        except CustomUser.DoesNotExist:
            return Response({'status': 'error', 'message': 'Child not found in family'}, status=status.HTTP_404_NOT_FOUND)

        latest = LocationLog.objects.filter(child=child).order_by('-timestamp').first()
        if not latest:
            return Response({'status': 'success', 'location': None}, status=status.HTTP_200_OK)

        serializer = LocationLogOutputSerializer(latest)
        return Response({'status': 'success', 'location': serializer.data}, status=status.HTTP_200_OK)


class ParentChildSafetyAlertsView(APIView):
    permission_classes = [IsAuthenticated, IsParent]

    def get(self, request, child_id):
        try:
            child = CustomUser.objects.get(id=child_id, family=request.user.family, role='CHILD')
        except CustomUser.DoesNotExist:
            return Response({'status': 'error', 'message': 'Child not found in family'}, status=status.HTTP_404_NOT_FOUND)

        alerts = SafetyAlert.objects.filter(child=child).order_by('-notified_at')[:50]
        serializer = SafetyAlertSerializer(alerts, many=True)
        return Response({'status': 'success', 'alerts': serializer.data}, status=status.HTTP_200_OK)

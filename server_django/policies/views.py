from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from accounts.models import CustomUser
from accounts.permissions import IsParent, IsChild
from .models import Device, AppPolicy, ScheduleRule, AllowedYouTubeChannel
from .serializers import (
    DeviceSerializer,
    AppPolicySerializer,
    ScheduleRuleSerializer,
    AllowedYouTubeChannelSerializer,
)

class DeviceEnrollView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = DeviceSerializer(data=request.data)
        if serializer.is_valid():
            device_name = serializer.validated_data.get('device_name', 'Child Phone')
            device, created = Device.objects.update_or_create(
                user=request.user,
                device_name=device_name,
                defaults={
                    'device_model': serializer.validated_data.get('device_model', ''),
                    'os_version': serializer.validated_data.get('os_version', ''),
                    'app_version': serializer.validated_data.get('app_version', ''),
                    'fcm_token': serializer.validated_data.get('fcm_token', ''),
                    'is_active': True,
                }
            )
            return Response({
                'status': 'success',
                'device_id': str(device.id),
                'device': DeviceSerializer(device).data
            }, status=status.HTTP_201_CREATED if created else status.HTTP_200_OK)
        return Response({'status': 'error', 'errors': serializer.errors}, status=status.HTTP_400_BAD_REQUEST)


class ChildPolicySyncView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, child_id):
        try:
            child = CustomUser.objects.get(id=child_id, role='CHILD')
        except CustomUser.DoesNotExist:
            return Response({'status': 'error', 'message': 'Child not found'}, status=status.HTTP_404_NOT_FOUND)

        # Authorization check: Parent must belong to same family, or user is the child themselves
        if request.user.role == 'PARENT' and child.family_id != request.user.family_id:
            return Response({'status': 'error', 'message': 'Forbidden'}, status=status.HTTP_403_FORBIDDEN)
        if request.user.role == 'CHILD' and str(request.user.id) != str(child_id):
            return Response({'status': 'error', 'message': 'Forbidden'}, status=status.HTTP_403_FORBIDDEN)

        since_version = int(request.query_params.get('since_version', 0))

        policies = AppPolicy.objects.filter(child=child)
        schedules = ScheduleRule.objects.filter(child=child)
        youtube_channels = AllowedYouTubeChannel.objects.filter(child=child)

        max_version = max(
            [p.version for p in policies] +
            [s.version for s in schedules] +
            [y.version for y in youtube_channels] +
            [1]
        )

        return Response({
            'status': 'success',
            'version': max_version,
            'policies': AppPolicySerializer(policies, many=True).data,
            'schedules': ScheduleRuleSerializer(schedules, many=True).data,
            'allowed_youtube_channels': AllowedYouTubeChannelSerializer(youtube_channels, many=True).data,
        }, status=status.HTTP_200_OK)


class ParentAppPolicyUpdateView(APIView):
    permission_classes = [IsAuthenticated, IsParent]

    def post(self, request, child_id):
        try:
            child = CustomUser.objects.get(id=child_id, family=request.user.family, role='CHILD')
        except CustomUser.DoesNotExist:
            return Response({'status': 'error', 'message': 'Child not found in family'}, status=status.HTTP_404_NOT_FOUND)

        package_name = request.data.get('package_name')
        app_name = request.data.get('app_name', package_name)
        is_blocked = request.data.get('is_blocked', False)
        daily_limit = request.data.get('daily_limit_minutes', 0)

        if not package_name:
            return Response({'status': 'error', 'message': 'package_name is required'}, status=status.HTTP_400_BAD_REQUEST)

        policy, _ = AppPolicy.objects.update_or_create(
            child=child,
            package_name=package_name,
            defaults={
                'app_name': app_name,
                'is_blocked': is_blocked,
                'daily_limit_minutes': daily_limit,
            }
        )
        return Response({
            'status': 'success',
            'policy': AppPolicySerializer(policy).data
        }, status=status.HTTP_200_OK)


class ParentScheduleRuleCreateView(APIView):
    permission_classes = [IsAuthenticated, IsParent]

    def post(self, request, child_id):
        try:
            child = CustomUser.objects.get(id=child_id, family=request.user.family, role='CHILD')
        except CustomUser.DoesNotExist:
            return Response({'status': 'error', 'message': 'Child not found in family'}, status=status.HTTP_404_NOT_FOUND)

        serializer = ScheduleRuleSerializer(data=request.data)
        if serializer.is_valid():
            rule = serializer.save(child=child)
            return Response({
                'status': 'success',
                'rule': ScheduleRuleSerializer(rule).data
            }, status=status.HTTP_201_CREATED)
        return Response({'status': 'error', 'errors': serializer.errors}, status=status.HTTP_400_BAD_REQUEST)


class ParentYouTubeWhitelistView(APIView):
    permission_classes = [IsAuthenticated, IsParent]

    def post(self, request, child_id):
        try:
            child = CustomUser.objects.get(id=child_id, family=request.user.family, role='CHILD')
        except CustomUser.DoesNotExist:
            return Response({'status': 'error', 'message': 'Child not found in family'}, status=status.HTTP_404_NOT_FOUND)

        channel_id = request.data.get('channel_id')
        channel_title = request.data.get('channel_title')
        channel_handle = request.data.get('channel_handle', '')
        is_allowed = request.data.get('is_allowed', True)

        if not channel_id or not channel_title:
            return Response({'status': 'error', 'message': 'channel_id and channel_title are required'}, status=status.HTTP_400_BAD_REQUEST)

        yt_channel, _ = AllowedYouTubeChannel.objects.update_or_create(
            child=child,
            channel_id=channel_id,
            defaults={
                'channel_title': channel_title,
                'channel_handle': channel_handle,
                'is_allowed': is_allowed,
            }
        )
        return Response({
            'status': 'success',
            'channel': AllowedYouTubeChannelSerializer(yt_channel).data
        }, status=status.HTTP_200_OK)

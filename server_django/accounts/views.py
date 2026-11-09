from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework_simplejwt.views import TokenRefreshView
from .models import CustomUser
from .permissions import IsParent
from .serializers import (
    ParentRegisterSerializer,
    CreateChildAccountSerializer,
    UnifiedLoginSerializer,
    UserProfileSerializer,
)

class HealthCheckView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        return Response({
            'status': 'success',
            'service': 'Django REST API',
            'database': 'connected',
            'timestamp': 1700000000
        }, status=status.HTTP_200_OK)


class ParentRegisterView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = ParentRegisterSerializer(data=request.data)
        if serializer.is_valid():
            result = serializer.save()
            user_data = UserProfileSerializer(result['user']).data
            return Response({
                'status': 'success',
                'user': user_data,
                'tokens': result['tokens']
            }, status=status.HTTP_201_CREATED)
        return Response({'status': 'error', 'errors': serializer.errors}, status=status.HTTP_400_BAD_REQUEST)


class UnifiedLoginView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = UnifiedLoginSerializer(data=request.data)
        if serializer.is_valid():
            result = serializer.validated_data
            user_data = UserProfileSerializer(result['user']).data
            return Response({
                'status': 'success',
                'user': user_data,
                'tokens': result['tokens']
            }, status=status.HTTP_200_OK)
        return Response({'status': 'error', 'errors': serializer.errors}, status=status.HTTP_400_BAD_REQUEST)


class FamilyChildrenView(APIView):
    permission_classes = [IsAuthenticated, IsParent]

    def get(self, request):
        # List all children belonging to the authenticated parent's family
        children = CustomUser.objects.filter(
            family=request.user.family,
            role='CHILD'
        ).order_by('-date_joined')
        serializer = UserProfileSerializer(children, many=True)
        return Response({'status': 'success', 'children': serializer.data}, status=status.HTTP_200_OK)

    def post(self, request):
        # Parent creates a new child account
        serializer = CreateChildAccountSerializer(data=request.data, context={'request': request})
        if serializer.is_valid():
            child = serializer.save()
            return Response({
                'status': 'success',
                'child': UserProfileSerializer(child).data
            }, status=status.HTTP_201_CREATED)
        return Response({'status': 'error', 'errors': serializer.errors}, status=status.HTTP_400_BAD_REQUEST)


class UserProfileView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        serializer = UserProfileSerializer(request.user)
        return Response({'status': 'success', 'user': serializer.data}, status=status.HTTP_200_OK)

import uuid
from rest_framework import serializers
from rest_framework_simplejwt.tokens import RefreshToken
from .models import CustomUser, Family

class UserProfileSerializer(serializers.ModelSerializer):
    family_id = serializers.UUIDField(source='family.id', read_only=True)
    family_name = serializers.CharField(source='family.family_name', read_only=True)

    class Meta:
        model = CustomUser
        fields = ['id', 'email', 'username', 'role', 'display_name', 'family_id', 'family_name', 'date_joined']
        read_only_fields = fields


class ParentRegisterSerializer(serializers.Serializer):
    email = serializers.EmailField(required=True)
    password = serializers.CharField(write_only=True, min_length=6, required=True)
    display_name = serializers.CharField(max_length=128, required=True)
    family_name = serializers.CharField(max_length=128, required=False, default="")

    def validate_email(self, value):
        clean_email = value.lower().strip()
        if CustomUser.objects.filter(email__iexact=clean_email).exists() or CustomUser.objects.filter(username__iexact=clean_email).exists():
            raise serializers.ValidationError("An account with this email already exists.")
        return clean_email

    def create(self, validated_data):
        email = validated_data['email']
        password = validated_data['password']
        display_name = validated_data['display_name'].strip()
        family_name = validated_data.get('family_name', '').strip() or f"{display_name}'s Family"

        family = Family.objects.create(
            family_name=family_name,
            invite_code=str(uuid.uuid4())[:8].upper()
        )
        user = CustomUser.objects.create_user(
            email=email,
            username=email,
            password=password,
            display_name=display_name,
            role='PARENT',
            family=family
        )
        refresh = RefreshToken.for_user(user)
        return {
            'user': user,
            'family': family,
            'tokens': {
                'access': str(refresh.access_token),
                'refresh': str(refresh)
            }
        }


class CreateChildAccountSerializer(serializers.Serializer):
    username = serializers.CharField(max_length=64, required=True)
    password = serializers.CharField(write_only=True, min_length=6, required=True)
    display_name = serializers.CharField(max_length=128, required=True)

    def validate_username(self, value):
        clean_username = value.lower().strip()
        if ' ' in clean_username:
            raise serializers.ValidationError("Username cannot contain spaces.")
        if CustomUser.objects.filter(username__iexact=clean_username).exists():
            raise serializers.ValidationError("This username is already taken. Please choose another.")
        return clean_username

    def create(self, validated_data):
        parent = self.context['request'].user
        child = CustomUser.objects.create_user(
            username=validated_data['username'],
            password=validated_data['password'],
            display_name=validated_data['display_name'].strip(),
            role='CHILD',
            family=parent.family,
            created_by=parent
        )
        return child


class UnifiedLoginSerializer(serializers.Serializer):
    identifier = serializers.CharField(required=True)
    password = serializers.CharField(write_only=True, required=True)

    def validate(self, attrs):
        identifier = attrs.get('identifier', '').strip().lower()
        password = attrs.get('password')

        user = CustomUser.objects.filter(email__iexact=identifier).first() or \
               CustomUser.objects.filter(username__iexact=identifier).first()

        if not user or not user.check_password(password):
            raise serializers.ValidationError("Invalid credentials. Please verify your identifier and password.")

        if not user.is_active:
            raise serializers.ValidationError("This account has been deactivated.")

        refresh = RefreshToken.for_user(user)
        return {
            'user': user,
            'tokens': {
                'access': str(refresh.access_token),
                'refresh': str(refresh)
            }
        }

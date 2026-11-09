from rest_framework.test import APITestCase
from rest_framework import status
from accounts.models import CustomUser, Family

class TestPolicyAndTelemetryIntegration(APITestCase):

    def setUp(self):
        super().setUp()
        # Create Parent and Family
        self.family = Family.objects.create(family_name="Test Family", invite_code="ABC12345")
        self.parent = CustomUser.objects.create_user(
            email="parent@test.com",
            username="parent@test.com",
            password="Password123!",
            display_name="Test Parent",
            role="PARENT",
            family=self.family
        )
        # Create Child
        self.child = CustomUser.objects.create_user(
            username="child_alex",
            password="Password123!",
            display_name="Alex",
            role="CHILD",
            family=self.family,
            created_by=self.parent
        )

        # Obtain JWT for Parent
        res = self.client.post('/api/v1/auth/login/', {'identifier': 'parent@test.com', 'password': 'Password123!'})
        self.parent_token = res.data['tokens']['access']

        # Obtain JWT for Child
        res_child = self.client.post('/api/v1/auth/login/', {'identifier': 'child_alex', 'password': 'Password123!'})
        self.child_token = res_child.data['tokens']['access']

    def test_device_enrollment_and_policy_workflow(self):
        # 1. Child enrolls device
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.child_token}')
        enroll_res = self.client.post('/api/v1/devices/enroll/', {
            'device_name': 'Alex Pixel 8',
            'device_model': 'Pixel 8',
            'os_version': '14',
            'app_version': '2.0',
            'fcm_token': 'fcm_sample_token_123'
        }, format='json')
        assert enroll_res.status_code == status.HTTP_201_CREATED, enroll_res.data
        device_id = enroll_res.data['device_id']

        # 2. Parent updates app policy (block TikTok)
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.parent_token}')
        policy_res = self.client.post(f'/api/v1/children/{self.child.id}/policies/apps/', {
            'package_name': 'com.zhiliaoapp.musically',
            'app_name': 'TikTok',
            'is_blocked': True,
            'daily_limit_minutes': 30
        }, format='json')
        assert policy_res.status_code == status.HTTP_200_OK, policy_res.data

        # 3. Parent adds schedule rule (Bedtime)
        schedule_res = self.client.post(f'/api/v1/children/{self.child.id}/schedules/', {
            'rule_name': 'Bedtime Routine',
            'rule_type': 'BEDTIME',
            'days_of_week': [1, 2, 3, 4, 5],
            'start_time': '21:00:00',
            'end_time': '06:00:00',
            'is_active': True
        }, format='json')
        assert schedule_res.status_code == status.HTTP_201_CREATED, schedule_res.data

        # 4. Parent adds allowed YouTube channel
        yt_res = self.client.post(f'/api/v1/children/{self.child.id}/youtube/', {
            'channel_id': 'UC_x5XG1OV2P6uZZ5FSM9Ttw',
            'channel_title': 'Google Developers',
            'channel_handle': '@GoogleDevelopers',
            'is_allowed': True
        }, format='json')
        assert yt_res.status_code == status.HTTP_200_OK, yt_res.data

        # 5. Child syncs policies
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.child_token}')
        sync_res = self.client.get(f'/api/v1/children/{self.child.id}/policies/')
        assert sync_res.status_code == status.HTTP_200_OK, sync_res.data
        assert len(sync_res.data['policies']) == 1
        assert sync_res.data['policies'][0]['package_name'] == 'com.zhiliaoapp.musically'
        assert len(sync_res.data['schedules']) == 1
        assert len(sync_res.data['allowed_youtube_channels']) == 1

        # 6. Child uploads telemetry activity log (flagged cyberbullying threat)
        log_uuid = '123e4567-e89b-12d3-a456-426614174000'
        telemetry_res = self.client.post('/api/v1/telemetry/activities/', {
            'device_id': device_id,
            'logs': [
                {
                    'id': log_uuid,
                    'package_name': 'com.instagram.android',
                    'app_name': 'Instagram',
                    'content_title': 'Direct Message',
                    'threat_category': 'Cyberbullying',
                    'confidence_score': 0.92,
                    'ai_analysis_summary': 'Harassing text detected',
                    'is_flagged': True,
                    'timestamp': 1794132000000
                }
            ]
        }, format='json')
        assert telemetry_res.status_code == status.HTTP_200_OK, telemetry_res.data
        assert log_uuid in telemetry_res.data['accepted_ids']

        # 7. Parent checks activity feed and safety alerts
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.parent_token}')
        feed_res = self.client.get(f'/api/v1/children/{self.child.id}/activity-feed/?flagged_only=true')
        assert feed_res.status_code == status.HTTP_200_OK, feed_res.data
        assert len(feed_res.data['results']) == 1
        assert feed_res.data['results'][0]['threat_category'] == 'Cyberbullying'

        alerts_res = self.client.get(f'/api/v1/children/{self.child.id}/alerts/')
        assert alerts_res.status_code == status.HTTP_200_OK, alerts_res.data
        assert len(alerts_res.data['alerts']) == 1
        assert alerts_res.data['alerts'][0]['severity'] == 'CRITICAL'

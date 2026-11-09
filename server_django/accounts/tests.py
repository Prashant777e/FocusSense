from rest_framework.test import APITestCase
from rest_framework import status
from accounts.models import CustomUser, Family

class TestFocusSenseAuthenticationFlow(APITestCase):
    """
    Automated integration tests for Task 1:
    1. Parent registers and logs in.
    2. Parent creates two child accounts.
    3. Both children can independently log in and receive their own JWT tokens.
    4. Child account cannot create other accounts (role-based security).
    """

    def setUp(self):
        super().setUp()


    def test_complete_parent_and_children_authentication_flow(self):
        # -------------------------------------------------------------
        # STEP 1: Parent Registers
        # -------------------------------------------------------------
        register_url = '/api/v1/auth/parent/register/'
        parent_payload = {
            'email': 'sarah.parent@focussense.com',
            'password': 'SecureParentPassword123!',
            'display_name': 'Sarah Connor',
            'family_name': 'Connor Safe Family'
        }
        res_register = self.client.post(register_url, parent_payload, format='json')
        assert res_register.status_code == status.HTTP_201_CREATED, res_register.data
        assert res_register.data['status'] == 'success'
        assert 'tokens' in res_register.data
        assert 'access' in res_register.data['tokens']
        assert 'refresh' in res_register.data['tokens']
        assert res_register.data['user']['role'] == 'PARENT'
        assert res_register.data['user']['email'] == 'sarah.parent@focussense.com'
        parent_tokens = res_register.data['tokens']

        # -------------------------------------------------------------
        # STEP 2: Parent Logs In
        # -------------------------------------------------------------
        login_url = '/api/v1/auth/login/'
        login_payload = {
            'identifier': 'sarah.parent@focussense.com',
            'password': 'SecureParentPassword123!'
        }
        res_login = self.client.post(login_url, login_payload, format='json')
        assert res_login.status_code == status.HTTP_200_OK, res_login.data
        assert res_login.data['user']['role'] == 'PARENT'
        parent_access_token = res_login.data['tokens']['access']

        # Set Parent Authorization Header for subsequent actions
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {parent_access_token}')

        # -------------------------------------------------------------
        # STEP 3: Parent Creates Child 1 ("John")
        # -------------------------------------------------------------
        children_url = '/api/v1/family/children/'
        child1_payload = {
            'username': 'john_connor',
            'password': 'JohnPassword123',
            'display_name': 'John Connor'
        }
        res_child1 = self.client.post(children_url, child1_payload, format='json')
        assert res_child1.status_code == status.HTTP_201_CREATED, res_child1.data
        assert res_child1.data['child']['role'] == 'CHILD'
        assert res_child1.data['child']['username'] == 'john_connor'
        assert 'password' not in res_child1.data['child']  # Verify password never exposed!

        # -------------------------------------------------------------
        # STEP 4: Parent Creates Child 2 ("Tim")
        # -------------------------------------------------------------
        child2_payload = {
            'username': 'tim_connor',
            'password': 'TimPassword456',
            'display_name': 'Tim Connor'
        }
        res_child2 = self.client.post(children_url, child2_payload, format='json')
        assert res_child2.status_code == status.HTTP_201_CREATED, res_child2.data
        assert res_child2.data['child']['role'] == 'CHILD'
        assert res_child2.data['child']['username'] == 'tim_connor'

        # Verify Parent can list both children
        res_list = self.client.get(children_url)
        assert res_list.status_code == status.HTTP_200_OK
        assert len(res_list.data['children']) == 2

        # -------------------------------------------------------------
        # STEP 5: Child 1 Logs In Independently (Clear parent credentials)
        # -------------------------------------------------------------
        self.client.credentials()  # Clear auth header
        child1_login = self.client.post(login_url, {
            'identifier': 'john_connor',
            'password': 'JohnPassword123'
        }, format='json')
        assert child1_login.status_code == status.HTTP_200_OK, child1_login.data
        assert child1_login.data['user']['role'] == 'CHILD'
        assert child1_login.data['user']['username'] == 'john_connor'
        assert 'access' in child1_login.data['tokens']
        child1_token = child1_login.data['tokens']['access']

        # -------------------------------------------------------------
        # STEP 6: Child 2 Logs In Independently
        # -------------------------------------------------------------
        child2_login = self.client.post(login_url, {
            'identifier': 'tim_connor',
            'password': 'TimPassword456'
        }, format='json')
        assert child2_login.status_code == status.HTTP_200_OK, child2_login.data
        assert child2_login.data['user']['role'] == 'CHILD'
        assert child2_login.data['user']['username'] == 'tim_connor'
        assert 'access' in child2_login.data['tokens']

        # -------------------------------------------------------------
        # STEP 7: Security Guard Check - Child Cannot Create Child Accounts
        # -------------------------------------------------------------
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {child1_token}')
        unauthorized_attempt = self.client.post(children_url, {
            'username': 'rogue_child',
            'password': 'Password789',
            'display_name': 'Rogue Child'
        }, format='json')
        assert unauthorized_attempt.status_code == status.HTTP_403_FORBIDDEN

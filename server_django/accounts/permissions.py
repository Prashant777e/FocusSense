from rest_framework.permissions import BasePermission

class IsParent(BasePermission):
    """
    Allows access only to authenticated parent accounts.
    Child accounts cannot access endpoints protected by this permission.
    """
    message = "Only parent accounts have permission to perform this action."

    def has_permission(self, request, view):
        return bool(
            request.user and
            request.user.is_authenticated and
            getattr(request.user, 'role', None) == 'PARENT'
        )

class IsChild(BasePermission):
    """
    Allows access only to authenticated child accounts.
    """
    message = "Only child accounts have permission to perform this action."

    def has_permission(self, request, view):
        return bool(
            request.user and
            request.user.is_authenticated and
            getattr(request.user, 'role', None) == 'CHILD'
        )

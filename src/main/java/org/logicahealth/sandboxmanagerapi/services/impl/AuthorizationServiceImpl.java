package org.logicahealth.sandboxmanagerapi.services.impl;

import org.apache.http.HttpStatus;
import org.logicahealth.sandboxmanagerapi.controllers.UnauthorizedException;
import org.logicahealth.sandboxmanagerapi.model.*;
import org.logicahealth.sandboxmanagerapi.services.AuthorizationService;
import org.logicahealth.sandboxmanagerapi.services.OAuthService;
import org.logicahealth.sandboxmanagerapi.services.UserService;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    private OAuthService oAuthService;
    private UserService userService;

    @Inject
    public AuthorizationServiceImpl() {}

    @Inject
    public void setoAuthService(OAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    @Inject
    public void setUserService(UserService userService) {
        this.userService = userService;
    }
    
    // Check that the userId matches the authorized user in the request
    @Override
    public void checkUserAuthorization(final HttpServletRequest request, String userId) {
        String oauthUserId = oAuthService.getOAuthUserId(request);

        if (!userId.equalsIgnoreCase(oauthUserId)) {
            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
    }

    // Return userId of the authorized user in the request
    @Override
    public String getSystemUserId(final HttpServletRequest request) {
        return oAuthService.getOAuthUserId(request);
    }

    // Return email of the authorized user in the request
    @Override
    public String getUserName(final HttpServletRequest request) {
        return oAuthService.getOAuthUserName(request);
    }

    // Return user name of the authorized user in the request
    @Override
    public String getEmail(final HttpServletRequest request) {
        return oAuthService.getOAuthUserEmail(request);
    }

    // Return bearer token of the authorized user in the request
    @Override
    public String getBearerToken(final HttpServletRequest request) {
        return oAuthService.getBearerToken(request);
    }

    // Checks to see if the authorized User is a member of the Sandbox
    @Override
    public String checkSandboxUserReadAuthorization(final HttpServletRequest request, final Sandbox sandbox) {
        return checkSandboxMember(sandbox, oAuthService.getOAuthUserId(request));
    }

    // Can a User modify a given Item in a given Sandbox
    // 1) The User must be a member of the Sandbox
    // 2) The User must have the right to modify the Item
    //    a) If the Item is Private, the user must be the creator of the Item
    //    b) If the Item is Public
    //       i) If the Sandbox is Private, the User must have non-read-only rights to the Sandbox
    //       ii) If the Sandbox is Public, the User mush be a Sandbox Admin
    @Override
    public String checkSandboxUserModifyAuthorization(final HttpServletRequest request, final Sandbox sandbox, final AbstractSandboxItem abstractSandboxItem) {
        String oauthUserId = oAuthService.getOAuthUserId(request);
        if (!checkUserHasSystemRole(userService.findBySbmUserId(oauthUserId), SystemRole.ADMIN)) {
            //Fast fail for non-sandbox members
            oauthUserId = checkSandboxUserReadAuthorization(request, sandbox);

            if (abstractSandboxItem.getVisibility() == Visibility.PRIVATE) {
                if (abstractSandboxItem.getCreatedBy().getSbmUserId().equalsIgnoreCase(oauthUserId)) {
                    return oauthUserId;
                }
            } else { // Item is PUBLIC
                if (sandbox.getVisibility() == Visibility.PRIVATE) {
                    return checkSandboxUserNotReadOnlyAuthorization(request, sandbox);
                } else { // Sandbox is PUBLIC
                    if (checkUserHasSandboxRole(request, sandbox, Role.ADMIN)) {
                        return oauthUserId;
                    }
                }
            }
            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
        return oauthUserId;
    }

    @Override
    public String checkSystemUserDeleteSandboxAuthorization(final HttpServletRequest request, final Sandbox sandbox, final User user) {
        String oauthUserId = oAuthService.getOAuthUserId(request);
        if (!checkUserHasSystemRole(userService.findBySbmUserId(oauthUserId), SystemRole.ADMIN)) {
            // If the sandbox is PRIVATE, only an admin can delete.
            // If the sandbox is PUBLIC, a system sandbox creator or system admin can delete.

            if (checkSystemUserCanModifySandbox(oauthUserId, sandbox, user)) {
//                (sandbox.getVisibility() == Visibility.PRIVATE && sandbox.getCreatedBy().getSbmUserId().equalsIgnoreCase(oauthUserId))) {
                return oauthUserId;
            }
            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
        return oauthUserId;
    }

    @Override
    public String checkSystemUserCanRemoveUser(final HttpServletRequest request, final Sandbox sandbox, final User user) {
        String oauthUserId = oAuthService.getOAuthUserId(request);
        if (!checkUserHasSystemRole(userService.findBySbmUserId(oauthUserId), SystemRole.ADMIN)) {
            if  (user.getSbmUserId().equalsIgnoreCase(oauthUserId) ||
                    (sandbox.getVisibility() == Visibility.PRIVATE && checkUserHasSandboxRole(oauthUserId, sandbox, Role.ADMIN))) {
                return oauthUserId;
            }
            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
        return oauthUserId;
    }

    // Sandbox Admin rights
    @Override
    public String checkSystemUserCanModifySandboxAuthorization(final HttpServletRequest request, final Sandbox sandbox, final User user) {
        String oauthUserId = oAuthService.getOAuthUserId(request);
        if (!checkUserHasSystemRole(userService.findBySbmUserId(oauthUserId), SystemRole.ADMIN)) {
            // If the Sandbox is PRIVATE, only an Admin can modify.
            // If the Sandbox is PUBLIC, a system sandbox creator or system Admin can modify.
            if (checkSystemUserCanModifySandbox(oauthUserId, sandbox, user)) {
                return oauthUserId;
            }

            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
        return oauthUserId;
    }

    @Override
    public String checkSystemUserCanManageSandboxDataAuthorization(final HttpServletRequest request, final Sandbox sandbox, final User user) {
        String oauthUserId = oAuthService.getOAuthUserId(request);
        if (!checkUserHasSystemRole(userService.findBySbmUserId(oauthUserId), SystemRole.ADMIN)) {
            // If the Sandbox is PRIVATE, only an Admin or data manager can manage data.
            // If the Sandbox is PUBLIC, a system sandbox creator or system Admin can manage data.
            if (checkSystemUserCanModifySandbox(oauthUserId, sandbox, user) ||
                    (sandbox.getVisibility() == Visibility.PRIVATE && checkUserHasSandboxRole(oauthUserId, sandbox, Role.MANAGE_DATA))) {
                return oauthUserId;
            }

            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
        return oauthUserId;
    }

    // Can manage user's is for inviting users to a sandbox
    // Only Admin's can delete Users
    // Only Admin can invite Users, users with MANAGE_USERS role can't invite, since all the old invited users got this role by default and it is too hard to
    // revoke this role now given the current database setup, it is just easier to just allow the ADMIN user only to be able to invite other users
    // to their sandbox
    @Override
    public String checkSystemUserCanManageSandboxUsersAuthorization(final HttpServletRequest request, final Sandbox sandbox, final User user) {
        String oauthUserId = oAuthService.getOAuthUserId(request);
        if (!checkUserHasSystemRole(userService.findBySbmUserId(oauthUserId), SystemRole.ADMIN)) {
            // If the Sandbox is PRIVATE, only an Admin or data manager can manage users.
            // If the Sandbox is PUBLIC, a system sandbox creator or system Admin can manage users.
            if (checkSystemUserCanModifySandbox(oauthUserId, sandbox, user) ||
                    ((sandbox.getVisibility() == Visibility.PRIVATE && checkUserHasSandboxRole(oauthUserId, sandbox, Role.ADMIN)))) {
                return oauthUserId;
            }

            throw new UnauthorizedException("User is not authorized.");
        }
        return oauthUserId;
    }

    @Override
    public void checkUserSystemRole(final User user, final SystemRole role) {
        if (!checkUserHasSystemRole(user, role)) {

            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
    }

    @Override
    public void checkSystemUserCanMakeTransaction(Sandbox sandbox, User user) {
        if (!checkUserHasSystemRole(user, SystemRole.ADMIN)) {
            List<Sandbox> sandboxes = user.getSandboxes();
            if (!sandboxes.contains(sandbox) && !sandbox.getVisibility().equals(Visibility.PUBLIC)) {
                throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
            }
        }
    }

    @Override
    public void checkIfPersonaAndHasAuthority(Sandbox sandbox, UserPersona userPersona) {
        if (!sandbox.equals(userPersona.getSandbox())) {
            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
    }

//                                  Default Sandbox Item Visibility
//            *-------------------------------------------------------------------------------------*
//            |                       |                           |                                 |
//            |                       |      Private Sandbox      |          Public Sandbox         |
//            *-------------------------------------------------------------------------------------*
//            |                       |                           |                                 |
//  Sandbox   |         USER          |          PUBLIC           |              PRIVATE            |
//   Role     |                       |                           |                                 |
//            *-------------------------------------------------------------------------------------*
//            |                       |                           |                                 |
//            |        ADMIN          |          PUBLIC           |              PUBLIC             |
//            |                       |                           |                                 |
//            *-------------------------------------------------------------------------------------*

    @Override
    public Visibility getDefaultVisibility(final User user, final Sandbox sandbox) {

        // For a PRIVATE sandbox, non-readonly user's default visibility is PUBLIC.
        // For a PUBLIC sandbox, only ADMIN's have default visibility of PUBLIC.
        if ((sandbox.getVisibility() == Visibility.PRIVATE && !checkUserHasSandboxRole(user.getSbmUserId(), sandbox, Role.READONLY)) ||
                checkUserHasSandboxRole(user.getSbmUserId(), sandbox, Role.ADMIN)) {
            return Visibility.PUBLIC;
        }
        return Visibility.PRIVATE;
    }

    @Override
    public boolean checkUserHasSystemRole(final User user, final SystemRole role) {
        for(SystemRole systemRole : user.getSystemRoles()) {
            if (systemRole == role) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String checkSandboxUserNotReadOnlyAuthorization(final HttpServletRequest request, final Sandbox sandbox) {

        String oauthUserId = oAuthService.getOAuthUserId(request);
        if (!checkUserHasSystemRole(userService.findBySbmUserId(oauthUserId), SystemRole.ADMIN)) {
            if (!checkUserHasSandboxRole(oauthUserId, sandbox, Role.READONLY)) {
                return oauthUserId;
            }

            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
        return oauthUserId;
    }

    private boolean checkSystemUserCanModifySandbox(final String oauthUserId, final Sandbox sandbox, final User user) {
        // If the Sandbox is PRIVATE, only an Admin can modify.
        // If the Sandbox is PUBLIC, a system sandbox creator or system Admin can modify.
        return  (user.getSbmUserId().equalsIgnoreCase(oauthUserId) &&
                ((sandbox.getVisibility() == Visibility.PRIVATE && checkUserHasSandboxRole(oauthUserId, sandbox, Role.ADMIN)) ||
                        checkUserHasSystemRole(user, SystemRole.ADMIN)));
    }

    private String checkSandboxMember(final Sandbox sandbox, final String sbmUserId) {
        var user = userService.findBySbmUserId(sbmUserId);
        if (user != null && !checkUserHasSystemRole(user, SystemRole.ADMIN)) {
            for (UserRole userRole : sandbox.getUserRoles()) {
                if (userRole.getUser().getSbmUserId().equalsIgnoreCase(sbmUserId)) {
                    return sbmUserId;
                }
            }
            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
        return sbmUserId;
    }

    private boolean checkUserHasSandboxRole(final HttpServletRequest request, final Sandbox sandbox, final Role role) {
        String oauthUserId = oAuthService.getOAuthUserId(request);
        return checkUserHasSandboxRole(oauthUserId, sandbox, role);
    }

    private boolean checkUserHasSandboxRole(final String oauthUserId, final Sandbox sandbox, final Role role) {
        for(UserRole userRole : sandbox.getUserRoles()) {
            if (userRole.getUser().getSbmUserId().equalsIgnoreCase(oauthUserId) && userRole.getRole() == role) {
                return true;
            }
        }
        return false;
    }
}

package org.hspconsortium.sandboxmanagerapi.controllers;

import org.apache.http.HttpStatus;
import org.hspconsortium.sandboxmanagerapi.model.NewsItem;
import org.hspconsortium.sandboxmanagerapi.model.Notification;
import org.hspconsortium.sandboxmanagerapi.model.SystemRole;
import org.hspconsortium.sandboxmanagerapi.model.User;
import org.hspconsortium.sandboxmanagerapi.services.NotificationService;
import org.hspconsortium.sandboxmanagerapi.services.OAuthService;
import org.hspconsortium.sandboxmanagerapi.services.UserService;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping({"/notification"})
public class NotificationController extends AbstractController {

    private UserService userService;
    private NotificationService notificationService;

    @Inject
    NotificationController(final OAuthService oAuthService, final NotificationService notificationService, final UserService userService) {
        super(oAuthService);
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping(value = "", params = {"userId"})
    public List<Notification> getAllNotificationsByUser(HttpServletRequest request, @RequestParam(value = "userId") String userIdEncoded) throws UnsupportedEncodingException {
        String userId = java.net.URLDecoder.decode(userIdEncoded, StandardCharsets.UTF_8.name());
        checkUserAuthorization(request, userId);
        User user = userService.findBySbmUserId(userId);
        return notificationService.getAllNotificationsByUser(user);
    }

    @PostMapping(value = "", params = {"userId"})
    public void createNotificationsForAllUsers(HttpServletRequest request, @RequestParam(value = "userId") String userId, @RequestBody final NewsItem newsItem) {
        checkUserAuthorization(request, userId);
        User user = userService.findBySbmUserId(userId);
        Boolean isSystemAdmin = checkUserHasSystemRole(user, SystemRole.ADMIN);
        if (isSystemAdmin) {
            notificationService.createNotificationsForAllUsers(newsItem);
        } else {
            throw new UnauthorizedException(String.format(UNAUTHORIZED_ERROR, HttpStatus.SC_UNAUTHORIZED));
        }
    }

    @PutMapping(value = "/mark-hidden", params = {"userId"})
    public List<Notification> markAllNotificationsAsHiddenByUser(HttpServletRequest request, @RequestParam(value = "userId") String userIdEncoded) throws UnsupportedEncodingException {
        String userId = java.net.URLDecoder.decode(userIdEncoded, StandardCharsets.UTF_8.name());
        checkUserAuthorization(request, userId);
        User user = userService.findBySbmUserId(userId);
        return notificationService.markAllNotificationsAsHiddenByUser(user);
    }

    @PutMapping(value = "/mark-seen", params = {"userId"})
    public List<Notification> markAllNotificationsAsSeenByUser(HttpServletRequest request, @RequestParam(value = "userId") String userIdEncoded) throws UnsupportedEncodingException {
        String userId = java.net.URLDecoder.decode(userIdEncoded, StandardCharsets.UTF_8.name());
        checkUserAuthorization(request, userId);
        User user = userService.findBySbmUserId(userId);
        return notificationService.markAllNotificationsAsSeenByUser(user);
    }

    @PutMapping(value = "/{id}", params = {"userId"})
    public Notification updateNotification(HttpServletRequest request, @PathVariable Integer id, @RequestParam(value = "userId") String userIdEncoded, @RequestBody final Notification notification) throws UnsupportedEncodingException {
        String userId = java.net.URLDecoder.decode(userIdEncoded, StandardCharsets.UTF_8.name());
        checkUserAuthorization(request, userId);
        return notificationService.update(notification);
    }

    @DeleteMapping(value = "", params = {"userId", "newsItemId"})
    public void deleteNotificationForAllUsers(HttpServletRequest request, @RequestParam(value = "userId") String userIdEncoded, @RequestParam(value = "newsItemId") Integer newsItemId) throws UnsupportedEncodingException {
        String userId = java.net.URLDecoder.decode(userIdEncoded, StandardCharsets.UTF_8.name());
        checkUserAuthorization(request, userId);
        notificationService.deleteNotificationForAllUsers(newsItemId);
    }

//    @DeleteMapping(value = "/{id}", params = {"userId"})
//    public void deleteNotification(HttpServletRequest request, @PathVariable Integer id, @RequestParam(value = "userId") String userIdEncoded, @RequestBody final Notification notification) throws UnsupportedEncodingException {
//        String userId = java.net.URLDecoder.decode(userIdEncoded, StandardCharsets.UTF_8.name());
//        checkUserAuthorization(request, userId);
//
//    }

}
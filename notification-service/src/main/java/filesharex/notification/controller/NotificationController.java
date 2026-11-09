package filesharex.notification.controller;

import filesharex.notification.model.CreateNotificationRequest;
import filesharex.notification.model.NotificationDTO;
import filesharex.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController
{

    private static final String CLERK_ID_HEADER = "X-Clerk-Id";

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<NotificationDTO> createNotification(
            @RequestBody CreateNotificationRequest request)
    {
        log.info("Create notification request for user: {}", request.getClerkId());
        NotificationDTO notification = notificationService.createNotification(request);
        return ResponseEntity.ok(notification);
    }

    @GetMapping
    public ResponseEntity<List<NotificationDTO>> getNotifications(
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {
        List<NotificationDTO> notifications = notificationService.getNotifications(clerkId);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationDTO>> getUnreadNotifications(
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {
        List<NotificationDTO> notifications = notificationService.getUnreadNotifications(clerkId);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {
        Long count = notificationService.getUnreadCount(clerkId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markAsRead(
            @PathVariable String id,
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {
        NotificationDTO notification = notificationService.markAsRead(id, clerkId);
        return ResponseEntity.ok(notification);
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {
        notificationService.markAllAsRead(clerkId);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteNotification(
            @PathVariable String id,
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {
        notificationService.deleteNotification(id, clerkId);
        return ResponseEntity.ok(Map.of("message", "Notification deleted"));
    }

    // Internal endpoint for other services to create notifications
    @PostMapping("/internal")
    public ResponseEntity<NotificationDTO> createInternalNotification(
            @RequestBody CreateNotificationRequest request)
    {
        log.info("Internal notification request from service for user: {}", request.getClerkId());
        NotificationDTO notification = notificationService.createNotification(request);
        return ResponseEntity.ok(notification);
    }
}

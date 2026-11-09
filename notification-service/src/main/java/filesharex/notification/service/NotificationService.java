package filesharex.notification.service;

import filesharex.notification.model.CreateNotificationRequest;
import filesharex.notification.model.Notification;
import filesharex.notification.model.NotificationDTO;
import filesharex.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService
{

    private final NotificationRepository notificationRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public NotificationDTO createNotification(CreateNotificationRequest request)
    {
        Notification notification = Notification.builder()
                .clerkId(request.getClerkId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .referenceId(request.getReferenceId())
                .referenceType(request.getReferenceType())
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification created for user {}: {}", request.getClerkId(), request.getTitle());

        return mapToDTO(saved);
    }

    public List<NotificationDTO> getNotifications(String clerkId)
    {
        return notificationRepository.findByClerkIdOrderByCreatedAtDesc(clerkId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<NotificationDTO> getUnreadNotifications(String clerkId)
    {
        return notificationRepository.findByClerkIdAndReadFalseOrderByCreatedAtDesc(clerkId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public Long getUnreadCount(String clerkId)
    {
        return notificationRepository.countByClerkIdAndReadFalse(clerkId);
    }

    public NotificationDTO markAsRead(String notificationId, String clerkId)
    {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getClerkId().equals(clerkId))
        {
            throw new RuntimeException("Notification does not belong to user");
        }

        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        log.info("Notification marked as read: {}", notificationId);

        return mapToDTO(saved);
    }

    public void markAllAsRead(String clerkId)
    {
        List<Notification> unread = notificationRepository.findByClerkIdAndReadFalseOrderByCreatedAtDesc(clerkId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        log.info("All notifications marked as read for user: {}", clerkId);
    }

    public void deleteNotification(String notificationId, String clerkId)
    {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getClerkId().equals(clerkId))
        {
            throw new RuntimeException("Notification does not belong to user");
        }

        notificationRepository.deleteById(notificationId);
        log.info("Notification deleted: {}", notificationId);
    }

    // Convenience methods for creating specific notification types

    public void sendLowCreditNotification(String clerkId, int creditsRemaining)
    {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .clerkId(clerkId)
                .type("CREDIT_LOW")
                .title("Low Credits Warning")
                .message("You have only " + creditsRemaining + " credits remaining. Consider purchasing more.")
                .referenceType("SYSTEM")
                .build();
        createNotification(request);
    }

    public void sendPaymentSuccessNotification(String clerkId, int creditsAdded, String orderId)
    {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .clerkId(clerkId)
                .type("PAYMENT_SUCCESS")
                .title("Payment Successful")
                .message("Your payment was successful. " + creditsAdded + " credits have been added to your account.")
                .referenceId(orderId)
                .referenceType("PAYMENT")
                .build();
        createNotification(request);
    }

    public void sendFileUploadedNotification(String clerkId, String fileName, String fileId)
    {
        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .clerkId(clerkId)
                .type("FILE_UPLOADED")
                .title("File Uploaded")
                .message("Your file '" + fileName + "' has been uploaded successfully.")
                .referenceId(fileId)
                .referenceType("FILE")
                .build();
        createNotification(request);
    }

    private NotificationDTO mapToDTO(Notification notification)
    {
        return NotificationDTO.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .read(notification.isRead())
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .createdAt(notification.getCreatedAt() != null ? notification.getCreatedAt().format(FORMATTER) : null)
                .build();
    }
}

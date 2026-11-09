package filesharex.notification.repository;

import filesharex.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String>
{

    List<Notification> findByClerkIdOrderByCreatedAtDesc(String clerkId);

    List<Notification> findByClerkIdAndReadFalseOrderByCreatedAtDesc(String clerkId);

    Long countByClerkIdAndReadFalse(String clerkId);
}

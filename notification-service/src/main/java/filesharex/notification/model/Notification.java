package filesharex.notification.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Notification
{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String clerkId;
    private String type; // CREDIT_LOW, PAYMENT_SUCCESS, FILE_UPLOADED, SYSTEM
    private String title;
    private String message;
    private boolean read;
    private String referenceId; // related entity ID (payment order, file ID, etc.)
    private String referenceType; // PAYMENT, FILE, SYSTEM

    @CreatedDate
    private LocalDateTime createdAt;
}

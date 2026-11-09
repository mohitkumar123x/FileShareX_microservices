package filesharex.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class NotificationDTO
{
    private String id;
    private String type;
    private String title;
    private String message;
    private boolean read;
    private String referenceId;
    private String referenceType;
    private String createdAt;
}

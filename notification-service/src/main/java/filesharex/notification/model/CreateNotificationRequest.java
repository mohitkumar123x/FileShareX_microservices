package filesharex.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CreateNotificationRequest
{
    private String clerkId;
    private String type;
    private String title;
    private String message;
    private String referenceId;
    private String referenceType;
}

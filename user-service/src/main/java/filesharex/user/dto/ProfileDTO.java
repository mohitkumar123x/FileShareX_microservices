package filesharex.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ProfileDTO
{
    private String id;
    private String clerkId;
    private String email;
    private String firstName;
    private String lastName;
    private String photoUrl;
    private Integer credits;
    private Instant createdAt;
}

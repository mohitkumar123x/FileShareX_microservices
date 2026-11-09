package filesharex.user.document;

import jakarta.persistence.Column;
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

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Entity
@Table(name = "profiles")
public class ProfileDocument
{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String clerkId;
    @Column(unique = true)
    private String email;
    private String firstName;
    private String lastName;
    private Integer credits;
    private String photoUrl;
    @CreatedDate
    private Instant createdAt;
}

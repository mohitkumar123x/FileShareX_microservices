package filesharex.user.document;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_credits")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCredits
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String clerkId;
    private Integer credits;
    private String plan; //BASIC, PREMIUM, ULTIMATE
}

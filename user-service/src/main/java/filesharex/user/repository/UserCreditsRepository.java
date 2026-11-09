package filesharex.user.repository;

import filesharex.user.document.UserCredits;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCreditsRepository extends JpaRepository<UserCredits, String>
{

    Optional<UserCredits> findByClerkId(String clerkId);
}

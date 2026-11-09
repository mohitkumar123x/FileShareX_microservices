package filesharex.user.repository;

import filesharex.user.document.ProfileDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileRepository extends JpaRepository<ProfileDocument, String>
{

    Optional<ProfileDocument> findByEmail(String email);

    ProfileDocument findByClerkId(String clerkId);

    Boolean existsByClerkId(String clerkId);
}

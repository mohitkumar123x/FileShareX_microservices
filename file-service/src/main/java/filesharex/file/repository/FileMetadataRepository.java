package filesharex.file.repository;

import filesharex.file.document.FileMetadataDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileMetadataRepository extends JpaRepository<FileMetadataDocument, String>
{

    List<FileMetadataDocument> findByClerkId(String clerkId);

    Long countByClerkId(String clerkId);
}

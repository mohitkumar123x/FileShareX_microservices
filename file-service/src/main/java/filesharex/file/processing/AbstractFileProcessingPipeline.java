package filesharex.file.processing;

import filesharex.file.document.FileMetadataDocument;
import filesharex.file.storage.FileStorageFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractFileProcessingPipeline
{

    protected final FileStorageFacade storageFacade;

    public final FileMetadataDocument execute(MultipartFile file, String clerkId)
    {
        log.info("Executing file processing pipeline for: {} (user: {})", file.getOriginalFilename(), clerkId);

        preProcess(file, clerkId);
        String key = storeFile(file, clerkId);
        FileMetadataDocument doc = buildMetadata(file, clerkId, key);
        postProcess(doc);

        log.info("File processing pipeline completed for: {} -> key: {}", file.getOriginalFilename(), key);
        return doc;
    }

    protected abstract void preProcess(MultipartFile file, String clerkId);

    protected abstract String storeFile(MultipartFile file, String clerkId);

    protected FileMetadataDocument buildMetadata(MultipartFile file, String clerkId, String key)
    {
        return FileMetadataDocument.builder()
                .fileLocation(key)
                .name(file.getOriginalFilename())
                .size(file.getSize())
                .type(file.getContentType())
                .clerkId(clerkId)
                .isPublic(false)
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    protected abstract void postProcess(FileMetadataDocument doc);
}

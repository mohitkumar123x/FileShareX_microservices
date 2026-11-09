package filesharex.file.processing;

import filesharex.file.document.FileMetadataDocument;
import filesharex.file.storage.FileStorageFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
public class PublicSharePipeline extends AbstractFileProcessingPipeline
{

    public PublicSharePipeline(FileStorageFacade storageFacade)
    {
        super(storageFacade);
    }

    @Override
    protected void preProcess(MultipartFile file, String clerkId)
    {
        log.debug("Running pre-processing for public share upload");
        // Public share may have different validation rules
    }

    @Override
    protected String storeFile(MultipartFile file, String clerkId)
    {
        log.debug("Storing file for public share");
        return storageFacade.store(file, clerkId);
    }

    @Override
    protected FileMetadataDocument buildMetadata(MultipartFile file, String clerkId, String key)
    {
        FileMetadataDocument doc = super.buildMetadata(file, clerkId, key);
        doc.setIsPublic(true);
        return doc;
    }

    @Override
    protected void postProcess(FileMetadataDocument doc)
    {
        log.debug("Public share post-processing for file: {}", doc.getName());
        // Public share may trigger notifications, but no credit consumption
    }
}

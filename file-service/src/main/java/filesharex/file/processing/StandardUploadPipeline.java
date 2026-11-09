package filesharex.file.processing;

import filesharex.file.document.FileMetadataDocument;
import filesharex.file.service.UserClient;
import filesharex.file.storage.FileStorageFacade;
import filesharex.file.validation.FileValidationChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
public class StandardUploadPipeline extends AbstractFileProcessingPipeline
{

    private final FileValidationChain validationChain;
    private final UserClient userClient;

    public StandardUploadPipeline(FileStorageFacade storageFacade,
                                   FileValidationChain validationChain,
                                   UserClient userClient)
    {
        super(storageFacade);
        this.validationChain = validationChain;
        this.userClient = userClient;
    }

    @Override
    protected void preProcess(MultipartFile file, String clerkId)
    {
        log.debug("Running pre-processing validation for standard upload");
        validationChain.validate(file, clerkId);
    }

    @Override
    protected String storeFile(MultipartFile file, String clerkId)
    {
        log.debug("Storing file via facade for standard upload");
        return storageFacade.store(file, clerkId);
    }

    @Override
    protected void postProcess(FileMetadataDocument doc)
    {
        log.debug("Consuming credit for user: {}", doc.getClerkId());
        userClient.consumeCredit(doc.getClerkId());
    }
}

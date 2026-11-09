package filesharex.file.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
public class FileValidationChain {

    private final FileValidationRule chain;

    public FileValidationChain(CreditValidationRule credit,
                               FileSizeValidationRule size,
                               FileTypeValidationRule type)
    {
        credit.chain(size).chain(type);
        this.chain = credit;
        log.info("File validation chain initialized: Credit -> Size -> Type");
    }

    public void validate(MultipartFile file, String clerkId)
    {
        log.debug("Starting validation chain for file: {} (user: {})", file.getOriginalFilename(), clerkId);
        chain.validate(file, clerkId);
        log.debug("Validation chain completed successfully for file: {}", file.getOriginalFilename());
    }
}

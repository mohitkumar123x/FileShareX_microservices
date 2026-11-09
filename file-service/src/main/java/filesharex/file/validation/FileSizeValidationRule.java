package filesharex.file.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
public class FileSizeValidationRule extends FileValidationRule
{

    @Value("${file.validation.max-size:5242880}")
    private long maxSize;

    @Override
    protected void doValidate(MultipartFile file, String clerkId)
    {
        if (file.getSize() > maxSize)
        {
            log.warn("File size validation failed for user {}: {} bytes exceeds max {} bytes",
                    clerkId, file.getSize(), maxSize);
            throw new RuntimeException("File size exceeds maximum allowed size of " + (maxSize / 1024 / 1024) + "MB");
        }
        log.debug("File size validation passed for file: {}", file.getOriginalFilename());
    }
}

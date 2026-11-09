package filesharex.file.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class FileTypeValidationRule extends FileValidationRule
{

    @Value("${file.validation.allowed-types:image/jpeg,image/png,image/gif,application/pdf,text/plain}")
    private String allowedTypesConfig;

    @Override
    protected void doValidate(MultipartFile file, String clerkId)
    {
        List<String> allowedTypes = Arrays.asList(allowedTypesConfig.split(","));
        String contentType = file.getContentType();

        if (contentType == null || !allowedTypes.contains(contentType))
        {
            log.warn("File type validation failed for user {}: {} not in allowed types",
                    clerkId, contentType);
            throw new RuntimeException("File type not allowed. Allowed types: " + allowedTypesConfig);
        }
        log.debug("File type validation passed for file: {} (type: {})", file.getOriginalFilename(), contentType);
    }
}

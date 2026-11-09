package filesharex.file.validation;

import filesharex.file.service.UserClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditValidationRule extends FileValidationRule
{

    private final UserClient userClient;

    @Override
    protected void doValidate(MultipartFile file, String clerkId)
    {
        int credits = userClient.getCredits(clerkId);
        if (credits <= 0)
        {
            log.warn("Credit validation failed for user {}: insufficient credits ({})", clerkId, credits);
            throw new RuntimeException("Not enough credits to upload files. Please purchase more credits.");
        }
        log.debug("Credit validation passed for user {}: {} credits available", clerkId, credits);
    }
}

package filesharex.file.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public abstract class FileValidationRule
{

    private FileValidationRule next;

    public FileValidationRule chain(FileValidationRule next)
    {
        this.next = next;
        return next;
    }

    public void validate(MultipartFile file, String clerkId)
    {
        doValidate(file, clerkId);
        if (next != null)
        {
            next.validate(file, clerkId);
        }
    }

    protected abstract void doValidate(MultipartFile file, String clerkId);
}

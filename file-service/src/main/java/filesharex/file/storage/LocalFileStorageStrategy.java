package filesharex.file.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
@Slf4j
public class LocalFileStorageStrategy implements FileStorageStrategy
{

    @Value("${file.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public String getType()
    {
        return "local";
    }

    @Override
    public void upload(String key, MultipartFile file)
    {
        try
        {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            Path targetLocation = uploadPath.resolve(key);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("Uploaded file locally: {}", key);
        }
        catch (Exception e)
        {
            log.error("Failed to upload file locally: {}", key, e);
            throw new RuntimeException("Failed to upload file locally", e);
        }
    }

    @Override
    public InputStream download(String key)
    {
        try
        {
            Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(key);
            return Files.newInputStream(filePath);
        }
        catch (Exception e)
        {
            log.error("Failed to download file locally: {}", key, e);
            throw new RuntimeException("Failed to download file locally", e);
        }
    }

    @Override
    public void delete(String key)
    {
        try
        {
            Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(key);
            Files.deleteIfExists(filePath);
            log.info("Deleted file locally: {}", key);
        }
        catch (Exception e)
        {
            log.error("Failed to delete file locally: {}", key, e);
            throw new RuntimeException("Failed to delete file locally", e);
        }
    }

    @Override
    public String getPublicUrl(String key)
    {
        return "/uploads/" + key;
    }
}

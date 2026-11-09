package filesharex.file.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileStorageFacade
{

    private final FileStorageFactory factory;

    @Value("${file.storage.type:r2}")
    private String storageType;

    public String store(MultipartFile file, String clerkId)
    {
        FileStorageStrategy strategy = factory.getStrategy(storageType);
        String key = buildKey(file, clerkId);
        strategy.upload(key, file);
        log.info("Stored file: {} with key: {} using strategy: {}", file.getOriginalFilename(), key, storageType);
        return key;
    }

    public InputStream retrieve(String fileLocation)
    {
        FileStorageStrategy strategy = factory.getStrategy(storageType);
        return strategy.download(fileLocation);
    }

    public void remove(String fileLocation)
    {
        FileStorageStrategy strategy = factory.getStrategy(storageType);
        strategy.delete(fileLocation);
        log.info("Removed file: {} using strategy: {}", fileLocation, storageType);
    }

    public String getDownloadUrl(String fileLocation)
    {
        FileStorageStrategy strategy = factory.getStrategy(storageType);
        return strategy.getPublicUrl(fileLocation);
    }

    private String buildKey(MultipartFile file, String clerkId)
    {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains("."))
        {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return String.format("uploads/%s/%s%s", clerkId, UUID.randomUUID(), extension);
    }
}

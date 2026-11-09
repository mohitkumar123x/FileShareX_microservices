package filesharex.file.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStorageStrategy
{

    String getType();

    void upload(String key, MultipartFile file);

    InputStream download(String key);

    void delete(String key);

    String getPublicUrl(String key);
}

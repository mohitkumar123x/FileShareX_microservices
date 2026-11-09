package filesharex.file.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.net.URI;

@Component
@Slf4j
public class R2FileStorageStrategy implements FileStorageStrategy
{

    @Value("${r2.endpoint}")
    private String endpoint;

    @Value("${r2.access-key}")
    private String accessKey;

    @Value("${r2.secret-key}")
    private String secretKey;

    @Value("${r2.bucket-name}")
    private String bucketName;

    @Value("${r2.public-url}")
    private String publicUrl;

    @Value("${r2.region:auto}")
    private String region;

    private S3Client s3Client;

    @PostConstruct
    public void init()
    {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank())
        {
            log.warn("R2 credentials not configured. R2 storage operations will fail if invoked.");
            return;
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        String configuredRegion = (region != null && !region.isBlank()) ? region : "auto";
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(configuredRegion))
                .forcePathStyle(true)
                .build();

        log.info("R2 S3 client initialized for bucket: {}", bucketName);
    }

    @Override
    public String getType()
    {
        return "r2";
    }

    @Override
    public void upload(String key, MultipartFile file)
    {
        try
        {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded file to R2: {}", key);
        }
        catch (Exception e)
        {
            log.error("Failed to upload file to R2: {}", key, e);
            throw new RuntimeException("Failed to upload file to R2", e);
        }
    }

    @Override
    public InputStream download(String key)
    {
        try
        {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(getRequest);
        }
        catch (Exception e)
        {
            log.error("Failed to download file from R2: {}", key, e);
            throw new RuntimeException("Failed to download file from R2", e);
        }
    }

    @Override
    public void delete(String key)
    {
        try
        {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Deleted file from R2: {}", key);
        }
        catch (Exception e)
        {
            log.error("Failed to delete file from R2: {}", key, e);
            throw new RuntimeException("Failed to delete file from R2", e);
        }
    }

    @Override
    public String getPublicUrl(String key)
    {
        return publicUrl + "/" + key;
    }
}

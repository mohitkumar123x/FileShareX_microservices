package filesharex.file.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
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
        AwsCredentialsProvider credentialsProvider;
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank())
        {
            credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        else
        {
            log.warn("R2/S3 access/secret keys not explicitly set in env; falling back to DefaultCredentialsProvider");
            credentialsProvider = DefaultCredentialsProvider.create();
        }

        String configuredRegion = (region != null && !region.isBlank() && !"auto".equalsIgnoreCase(region))
                ? region
                : "ap-southeast-2";

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(configuredRegion));

        if (endpoint != null && !endpoint.isBlank())
        {
            builder.endpointOverride(URI.create(endpoint));
            // Only force path style for non-AWS endpoints like Cloudflare R2 or MinIO.
            // AWS S3 buckets (created after 2020) reject path-style or return redirects.
            if (!endpoint.contains("amazonaws.com"))
            {
                builder.forcePathStyle(true);
            }
        }

        s3Client = builder.build();
        log.info("S3/R2 client initialized for bucket: {} (region: {})", bucketName, configuredRegion);
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

package filesharex.file.controller;

import filesharex.file.dto.FileMetadataDTO;
import filesharex.file.service.FileMetadataService;
import filesharex.file.storage.FileStorageFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
@Slf4j
public class FileController
{

    private static final String CLERK_ID_HEADER = "X-Clerk-Id";

    private final FileMetadataService fileMetadataService;
    private final FileStorageFacade storageFacade;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(
            @RequestPart("files") MultipartFile[] files,
            @RequestHeader(CLERK_ID_HEADER) String clerkId) throws IOException
    {

        log.info("Upload request from user: {} with {} files", clerkId, files.length);

        List<FileMetadataDTO> list = fileMetadataService.uploadFiles(files, clerkId);

        Map<String, Object> response = new HashMap<>();
        response.put("files", list);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getFilesForCurrentUser(
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {

        log.info("Get files request from user: {}", clerkId);
        List<FileMetadataDTO> files = fileMetadataService.getFiles(clerkId);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<?> getPublicFile(@PathVariable String id)
    {
        FileMetadataDTO file = fileMetadataService.getPublicFile(id);
        return ResponseEntity.ok(file);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) throws IOException
    {
        FileMetadataDTO downloadableFile = fileMetadataService.getDownloadableFile(id);
        var inputStream = storageFacade.retrieve(downloadableFile.getFileLocation());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + downloadableFile.getName() + "\"")
                .body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(
            @PathVariable String id,
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {

        log.info("Delete file request: {} from user: {}", id, clerkId);
        fileMetadataService.deleteFile(id, clerkId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-public")
    public ResponseEntity<?> togglePublic(
            @PathVariable String id,
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {

        log.info("Toggle public request: {} from user: {}", id, clerkId);
        FileMetadataDTO file = fileMetadataService.togglePublic(id, clerkId);
        return ResponseEntity.ok(file);
    }
}

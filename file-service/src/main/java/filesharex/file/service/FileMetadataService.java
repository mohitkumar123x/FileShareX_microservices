package filesharex.file.service;

import filesharex.file.document.FileMetadataDocument;
import filesharex.file.dto.FileMetadataDTO;
import filesharex.file.processing.AbstractFileProcessingPipeline;
import filesharex.file.processing.StandardUploadPipeline;
import filesharex.file.repository.FileMetadataRepository;
import filesharex.file.storage.FileStorageFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileMetadataService
{

    private final FileMetadataRepository fileMetadataRepository;
    private final FileStorageFacade storageFacade;
    private final StandardUploadPipeline standardUploadPipeline;

    public List<FileMetadataDTO> uploadFiles(MultipartFile[] files, String clerkId) throws IOException
    {
        List<FileMetadataDocument> savedFiles = new ArrayList<>();

        for (MultipartFile file : files)
        {
            FileMetadataDocument doc = standardUploadPipeline.execute(file, clerkId);
            savedFiles.add(fileMetadataRepository.save(doc));
        }

        return savedFiles.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<FileMetadataDTO> getFiles(String clerkId)
    {
        List<FileMetadataDocument> files = fileMetadataRepository.findByClerkId(clerkId);
        return files.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public FileMetadataDTO getPublicFile(String id)
    {
        Optional<FileMetadataDocument> fileOptional = fileMetadataRepository.findById(id);
        if (fileOptional.isEmpty() || !fileOptional.get().getIsPublic())
        {
            throw new RuntimeException("Unable to get the file");
        }
        return mapToDTO(fileOptional.get());
    }

    public FileMetadataDTO getDownloadableFile(String id)
    {
        FileMetadataDocument file = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));
        return mapToDTO(file);
    }

    public void deleteFile(String id, String clerkId)
    {
        FileMetadataDocument file = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!file.getClerkId().equals(clerkId))
        {
            throw new RuntimeException("File does not belong to current user");
        }

        storageFacade.remove(file.getFileLocation());
        fileMetadataRepository.deleteById(id);
        log.info("Deleted file {} for user {}", id, clerkId);
    }

    public FileMetadataDTO togglePublic(String id, String clerkId)
    {
        FileMetadataDocument file = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!file.getClerkId().equals(clerkId))
        {
            throw new RuntimeException("File does not belong to current user");
        }

        file.setIsPublic(!file.getIsPublic());
        fileMetadataRepository.save(file);
        return mapToDTO(file);
    }

    private FileMetadataDTO mapToDTO(FileMetadataDocument doc)
    {
        return FileMetadataDTO.builder()
                .id(doc.getId())
                .fileLocation(doc.getFileLocation())
                .name(doc.getName())
                .size(doc.getSize())
                .type(doc.getType())
                .clerkId(doc.getClerkId())
                .isPublic(doc.getIsPublic())
                .uploadedAt(doc.getUploadedAt())
                .downloadUrl(storageFacade.getDownloadUrl(doc.getFileLocation()))
                .build();
    }
}

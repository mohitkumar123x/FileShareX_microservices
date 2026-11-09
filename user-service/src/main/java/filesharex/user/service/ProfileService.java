package filesharex.user.service;

import filesharex.user.document.ProfileDocument;
import filesharex.user.dto.ProfileDTO;
import filesharex.user.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService
{

    private final ProfileRepository profileRepository;

    public ProfileDTO createProfile(ProfileDTO profileDTO)
    {
        if (profileRepository.existsByClerkId(profileDTO.getClerkId()))
        {
            return updateProfile(profileDTO);
        }

        ProfileDocument profile = ProfileDocument.builder()
                .clerkId(profileDTO.getClerkId())
                .email(profileDTO.getEmail())
                .firstName(profileDTO.getFirstName())
                .lastName(profileDTO.getLastName())
                .photoUrl(profileDTO.getPhotoUrl())
                .credits(5)
                .createdAt(Instant.now())
                .build();

        profile = profileRepository.save(profile);
        log.info("Created profile for user: {}", profileDTO.getClerkId());

        return mapToDTO(profile);
    }

    public ProfileDTO updateProfile(ProfileDTO profileDTO)
    {
        ProfileDocument existingProfile = profileRepository.findByClerkId(profileDTO.getClerkId());

        if (existingProfile != null)
        {
            if (profileDTO.getEmail() != null && !profileDTO.getEmail().isEmpty())
            {
                existingProfile.setEmail(profileDTO.getEmail());
            }
            if (profileDTO.getFirstName() != null && !profileDTO.getFirstName().isEmpty())
            {
                existingProfile.setFirstName(profileDTO.getFirstName());
            }
            if (profileDTO.getLastName() != null && !profileDTO.getLastName().isEmpty())
            {
                existingProfile.setLastName(profileDTO.getLastName());
            }
            if (profileDTO.getPhotoUrl() != null && !profileDTO.getPhotoUrl().isEmpty())
            {
                existingProfile.setPhotoUrl(profileDTO.getPhotoUrl());
            }

            profileRepository.save(existingProfile);
            log.info("Updated profile for user: {}", profileDTO.getClerkId());
            return mapToDTO(existingProfile);
        }
        return null;
    }

    public ProfileDocument getProfileByClerkId(String clerkId)
    {
        return profileRepository.findByClerkId(clerkId);
    }

    public boolean existsByClerkId(String clerkId)
    {
        return profileRepository.existsByClerkId(clerkId);
    }

    public void deleteProfile(String clerkId)
    {
        ProfileDocument existingProfile = profileRepository.findByClerkId(clerkId);
        if (existingProfile != null)
        {
            profileRepository.delete(existingProfile);
            log.info("Deleted profile for user: {}", clerkId);
        }
    }

    private ProfileDTO mapToDTO(ProfileDocument profile)
    {
        return ProfileDTO.builder()
                .id(profile.getId())
                .clerkId(profile.getClerkId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .photoUrl(profile.getPhotoUrl())
                .credits(profile.getCredits())
                .createdAt(profile.getCreatedAt())
                .build();
    }
}

package filesharex.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import filesharex.user.dto.ProfileDTO;
import filesharex.user.service.ProfileService;
import filesharex.user.service.UserCreditsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class ClerkWebhookController
{

    @Value("${clerk.webhook.secret}")
    private String webhookSecret;

    private final ProfileService profileService;
    private final UserCreditsService userCreditsService;

    @PostMapping("/clerk")
    public ResponseEntity<?> handleClerkWebhook(@RequestHeader("svix-id") String svixId,
                                                 @RequestHeader("svix-timestamp") String svixTimestamp,
                                                 @RequestHeader("svix-signature") String svixSignature,
                                                 @RequestBody String payload)
    {
        try
        {
            boolean isValid = verifyWebhookSignature(svixId, svixTimestamp, svixSignature, payload);
            if (!isValid)
            {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature");
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(payload);
            String eventType = rootNode.path("type").asText();

            switch (eventType)
            {
                case "user.created":
                    handleUserCreated(rootNode.path("data"));
                    break;
                case "user.updated":
                    handleUserUpdated(rootNode.path("data"));
                    break;
                case "user.deleted":
                    handleUserDeleted(rootNode.path("data"));
                    break;
            }
            return ResponseEntity.ok().build();
        }
        catch (Exception e)
        {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    private void handleUserDeleted(JsonNode data)
    {
        String clerkId = data.path("id").asText();
        profileService.deleteProfile(clerkId);
    }

    private void handleUserUpdated(JsonNode data)
    {
        String clerkId = data.path("id").asText();
        String email = extractEmail(data);
        String firstName = data.path("first_name").asText("");
        String lastName = data.path("last_name").asText("");
        String photoUrl = data.path("image_url").asText("");

        ProfileDTO updatedProfile = ProfileDTO.builder()
                .clerkId(clerkId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .photoUrl(photoUrl)
                .build();

        updatedProfile = profileService.updateProfile(updatedProfile);

        if (updatedProfile == null)
        {
            handleUserCreated(data);
        }
    }

    private void handleUserCreated(JsonNode data)
    {
        String clerkId = data.path("id").asText();
        String email = extractEmail(data);
        String firstName = data.path("first_name").asText("");
        String lastName = data.path("last_name").asText("");
        String photoUrl = data.path("image_url").asText("");

        ProfileDTO newProfile = ProfileDTO.builder()
                .clerkId(clerkId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .photoUrl(photoUrl)
                .build();

        profileService.createProfile(newProfile);
        userCreditsService.createInitialCredits(clerkId);
        log.info("User created via webhook: {}", clerkId);
    }

    private String extractEmail(JsonNode data)
    {
        String email = "";
        JsonNode emailAddresses = data.path("email_addresses");
        if (emailAddresses.isArray() && emailAddresses.size() > 0)
        {
            email = emailAddresses.get(0).path("email_address").asText();
        }
        return email;
    }

    private boolean verifyWebhookSignature(String svixId, String svixTimestamp, String svixSignature, String payload)
    {
        return true;
    }
}

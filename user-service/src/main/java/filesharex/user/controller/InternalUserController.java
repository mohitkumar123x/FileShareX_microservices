package filesharex.user.controller;

import filesharex.user.document.UserCredits;
import filesharex.user.dto.ProfileDTO;
import filesharex.user.service.ProfileService;
import filesharex.user.service.UserCreditsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController
{

    private final ProfileService profileService;
    private final UserCreditsService userCreditsService;

    @GetMapping("/{clerkId}")
    public ResponseEntity<ProfileDTO> getProfile(@PathVariable String clerkId)
    {
        var profile = profileService.getProfileByClerkId(clerkId);
        if (profile == null)
        {
            return ResponseEntity.notFound().build();
        }
        ProfileDTO dto = ProfileDTO.builder()
                .id(profile.getId())
                .clerkId(profile.getClerkId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .photoUrl(profile.getPhotoUrl())
                .credits(profile.getCredits())
                .createdAt(profile.getCreatedAt())
                .build();
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{clerkId}/credits")
    public ResponseEntity<Integer> getCredits(@PathVariable String clerkId)
    {
        UserCredits credits = userCreditsService.getUserCredits(clerkId);
        return ResponseEntity.ok(credits.getCredits());
    }

    @PostMapping("/{clerkId}/credits/consume")
    public ResponseEntity<Map<String, Object>> consumeCredit(@PathVariable String clerkId)
    {
        UserCredits credits = userCreditsService.consumeCredit(clerkId);
        if (credits == null)
        {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Not enough credits",
                    "credits", 0
            ));
        }
        return ResponseEntity.ok(Map.of(
                "credits", credits.getCredits(),
                "plan", credits.getPlan()
        ));
    }

    @PostMapping("/{clerkId}/credits/add")
    public ResponseEntity<Map<String, Object>> addCredits(
            @PathVariable String clerkId,
            @RequestBody Map<String, Object> request)
    {
        int amount = (int) request.get("amount");
        String plan = (String) request.get("plan");
        UserCredits credits = userCreditsService.addCredits(clerkId, amount, plan);
        return ResponseEntity.ok(Map.of(
                "credits", credits.getCredits(),
                "plan", credits.getPlan()
        ));
    }
}

package filesharex.user.controller;

import filesharex.user.document.UserCredits;
import filesharex.user.dto.UserCreditsDTO;
import filesharex.user.service.UserCreditsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserCreditsController
{

    private static final String CLERK_ID_HEADER = "X-Clerk-Id";

    private final UserCreditsService userCreditsService;

    @GetMapping("/credits")
    public ResponseEntity<?> getUserCredits(@RequestHeader(CLERK_ID_HEADER) String clerkId)
    {
        UserCredits userCredits = userCreditsService.getUserCredits(clerkId);
        UserCreditsDTO response = UserCreditsDTO.builder()
                .credits(userCredits.getCredits())
                .plan(userCredits.getPlan())
                .build();
        return ResponseEntity.ok(response);
    }
}

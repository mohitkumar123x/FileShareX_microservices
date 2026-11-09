package filesharex.user.service;

import filesharex.user.document.UserCredits;
import filesharex.user.event.CreditConsumedEvent;
import filesharex.user.event.CreditsAddedEvent;
import filesharex.user.repository.UserCreditsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCreditsService
{

    private final UserCreditsRepository userCreditsRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserCredits createInitialCredits(String clerkId)
    {
        UserCredits userCredits = UserCredits.builder()
                .clerkId(clerkId)
                .credits(5)
                .plan("BASIC")
                .build();
        log.info("Creating initial credits for user: {}", clerkId);
        return userCreditsRepository.save(userCredits);
    }

    public UserCredits getUserCredits(String clerkId)
    {
        return userCreditsRepository.findByClerkId(clerkId)
                .orElseGet(() -> createInitialCredits(clerkId));
    }

    public Boolean hasEnoughCredits(String clerkId, int requiredCredits)
    {
        UserCredits userCredits = getUserCredits(clerkId);
        return userCredits.getCredits() >= requiredCredits;
    }

    public UserCredits consumeCredit(String clerkId)
    {
        UserCredits userCredits = getUserCredits(clerkId);

        if (userCredits.getCredits() <= 0)
        {
            log.warn("Insufficient credits for user: {}", clerkId);
            return null;
        }

        userCredits.setCredits(userCredits.getCredits() - 1);
        UserCredits saved = userCreditsRepository.save(userCredits);

        eventPublisher.publishEvent(new CreditConsumedEvent(this, clerkId, saved.getCredits()));
        log.info("Credit consumed for user: {}, remaining: {}", clerkId, saved.getCredits());

        return saved;
    }

    public UserCredits addCredits(String clerkId, Integer creditsToAdd, String plan)
    {
        UserCredits userCredits = userCreditsRepository.findByClerkId(clerkId)
                .orElseGet(() -> createInitialCredits(clerkId));

        userCredits.setCredits(userCredits.getCredits() + creditsToAdd);
        userCredits.setPlan(plan);
        UserCredits saved = userCreditsRepository.save(userCredits);

        eventPublisher.publishEvent(new CreditsAddedEvent(this, clerkId, creditsToAdd, plan));
        log.info("Credits added for user: {}, added: {}, total: {}, plan: {}", clerkId, creditsToAdd, saved.getCredits(), plan);

        return saved;
    }
}

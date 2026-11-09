package filesharex.user.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserEventListener
{

    @EventListener
    public void handleCreditConsumed(CreditConsumedEvent event)
    {
        log.info("Credits consumed for user {}: {} remaining",
                event.getClerkId(), event.getCreditsRemaining());

        if (event.getCreditsRemaining() <= 2)
        {
            log.warn("Low credits warning for user {}: {} remaining",
                    event.getClerkId(), event.getCreditsRemaining());
        }
    }

    @EventListener
    public void handleCreditsAdded(CreditsAddedEvent event)
    {
        log.info("Credits added for user {}: +{} credits, plan={}",
                event.getClerkId(), event.getCreditsAdded(), event.getPlan());
    }
}

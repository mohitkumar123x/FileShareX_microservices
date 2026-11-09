package filesharex.user.event;

import org.springframework.context.ApplicationEvent;

public class CreditConsumedEvent extends ApplicationEvent
{

    private final String clerkId;
    private final int creditsRemaining;

    public CreditConsumedEvent(Object source, String clerkId, int creditsRemaining)
    {
        super(source);
        this.clerkId = clerkId;
        this.creditsRemaining = creditsRemaining;
    }

    public String getClerkId()
    {
        return clerkId;
    }

    public int getCreditsRemaining()
    {
        return creditsRemaining;
    }
}

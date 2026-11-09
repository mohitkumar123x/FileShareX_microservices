package filesharex.user.event;

import org.springframework.context.ApplicationEvent;

public class CreditsAddedEvent extends ApplicationEvent
{

    private final String clerkId;
    private final int creditsAdded;
    private final String plan;

    public CreditsAddedEvent(Object source, String clerkId, int creditsAdded, String plan)
    {
        super(source);
        this.clerkId = clerkId;
        this.creditsAdded = creditsAdded;
        this.plan = plan;
    }

    public String getClerkId()
    {
        return clerkId;
    }

    public int getCreditsAdded()
    {
        return creditsAdded;
    }

    public String getPlan()
    {
        return plan;
    }
}

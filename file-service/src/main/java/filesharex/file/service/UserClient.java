package filesharex.file.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@Slf4j
public class UserClient
{

    private final RestClient restClient;

    @Value("${services.user.base-url}")
    private String userBaseUrl;

    public UserClient(RestClient.Builder restClientBuilder)
    {
        this.restClient = restClientBuilder.build();
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "creditsFallback")
    public int getCredits(String clerkId)
    {
        log.debug("Fetching credits for user: {}", clerkId);
        Integer credits = restClient.get()
                .uri(userBaseUrl + "/internal/users/{clerkId}/credits", clerkId)
                .retrieve()
                .body(Integer.class);
        return credits != null ? credits : 0;
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "consumeFallback")
    public void consumeCredit(String clerkId)
    {
        log.debug("Consuming credit for user: {}", clerkId);
        restClient.post()
                .uri(userBaseUrl + "/internal/users/{clerkId}/credits/consume", clerkId)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unused")
    public int creditsFallback(String clerkId, Exception e)
    {
        log.warn("User Service unavailable for credit check: {}", e.getMessage());
        throw new RuntimeException("User Service is currently unavailable. Please try again later.");
    }

    @SuppressWarnings("unused")
    public void consumeFallback(String clerkId, Exception e)
    {
        log.warn("User Service unavailable for credit consumption: {}", e.getMessage());
        throw new RuntimeException("User Service is currently unavailable. Credit not consumed.");
    }
}

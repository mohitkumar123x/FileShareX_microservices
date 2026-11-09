package filesharex.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.PublicKey;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class ClerkJwtAuthFilter implements GlobalFilter, Ordered
{

    private static final String CLERK_ID_HEADER = "X-Clerk-Id";

    @Value("${clerk.issuer}")
    private String clerkIssuer;

    private final ClerkJwksProvider jwksProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
        String path = exchange.getRequest().getURI().getPath();

        // Skip auth for public endpoints
        if (path.contains("/webhooks") || path.contains("/public") ||
            path.contains("/download") || path.contains("/health"))
        {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer "))
        {
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        try
        {
            String token = authHeader.substring(7);
            String[] chunks = token.split("\\.");
            if (chunks.length < 3)
            {
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(chunks[0]));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode headerNode = mapper.readTree(headerJson);

            if (!headerNode.has("kid"))
            {
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            String kid = headerNode.get("kid").asText();
            PublicKey publicKey = jwksProvider.getPublicKey(kid);

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .setAllowedClockSkewSeconds(60)
                    .requireIssuer(clerkIssuer)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String clerkId = claims.getSubject();

            // Pass clerkId as header to downstream services
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(CLERK_ID_HEADER, clerkId)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }
        catch (Exception e)
        {
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder()
    {
        return -1; // Run early in the filter chain
    }
}

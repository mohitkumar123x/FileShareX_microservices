package filesharex.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@Slf4j
public class SecurityConfig
{

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http)
    {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/webhooks/**", "/files/public/**", "/files/download/**", "/health", "/actuator/**").permitAll()
                        .anyExchange().authenticated()
                )
                .build();
    }
}

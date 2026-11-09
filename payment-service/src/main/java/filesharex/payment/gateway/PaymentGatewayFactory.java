package filesharex.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PaymentGatewayFactory
{

    private final Map<String, PaymentGateway> gateways;

    public PaymentGatewayFactory(List<PaymentGateway> gatewayList)
    {
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(PaymentGateway::getProvider, Function.identity()));
        log.info("Initialized payment gateways: {}", gateways.keySet());
    }

    public PaymentGateway getGateway(String provider)
    {
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null)
        {
            throw new IllegalArgumentException("Unknown payment provider: " + provider);
        }
        return gateway;
    }
}

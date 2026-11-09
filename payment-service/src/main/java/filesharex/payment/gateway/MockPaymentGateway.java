package filesharex.payment.gateway;

import filesharex.payment.dto.PaymentDTO;
import filesharex.payment.dto.PaymentVerificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MockPaymentGateway implements PaymentGateway
{

    @Override
    public String getProvider()
    {
        return "mock";
    }

    @Override
    public PaymentResult createOrder(PaymentDTO dto)
    {
        String orderId = "mock_order_" + System.currentTimeMillis();
        log.info("Mock order created: {}", orderId);

        return PaymentResult.builder()
                .success(true)
                .orderId(orderId)
                .message("Mock order created successfully")
                .mockMode(true)
                .build();
    }

    @Override
    public PaymentResult verify(PaymentVerificationDTO dto)
    {
        log.info("Mock payment verified for order: {}", dto.getRazorpay_order_id());

        return PaymentResult.builder()
                .success(true)
                .paymentId("mock_payment_" + System.currentTimeMillis())
                .message("Mock payment verified successfully")
                .mockMode(true)
                .build();
    }
}

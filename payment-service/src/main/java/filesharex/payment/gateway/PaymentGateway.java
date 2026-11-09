package filesharex.payment.gateway;

import filesharex.payment.dto.PaymentDTO;
import filesharex.payment.dto.PaymentVerificationDTO;

public interface PaymentGateway
{

    String getProvider();

    PaymentResult createOrder(PaymentDTO dto);

    PaymentResult verify(PaymentVerificationDTO dto);
}

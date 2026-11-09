package filesharex.payment.gateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResult
{

    private boolean success;
    private String orderId;
    private String paymentId;
    private String message;
    private int creditsAdded;
    private String plan;
    private boolean mockMode;
}

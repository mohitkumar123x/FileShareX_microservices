package filesharex.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PaymentDTO
{

    private String orderId;
    private String planId;
    private Integer amount;
    private String currency;
    private Boolean success;
    private String message;
    private Integer credits;
    private Boolean isMock;
}

package filesharex.payment.gateway;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import filesharex.payment.dto.PaymentDTO;
import filesharex.payment.dto.PaymentVerificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

@Component
@Slf4j
public class RazorpayGateway implements PaymentGateway
{

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    @Override
    public String getProvider()
    {
        return "razorpay";
    }

    @Override
    public PaymentResult createOrder(PaymentDTO dto)
    {
        try
        {
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", dto.getAmount());
            orderRequest.put("currency", dto.getCurrency());
            orderRequest.put("receipt", "order_" + System.currentTimeMillis());

            Order order = razorpayClient.orders.create(orderRequest);

            log.info("Razorpay order created: {}", (String) order.get("id"));

            return PaymentResult.builder()
                    .success(true)
                    .orderId(order.get("id"))
                    .message("Order created successfully")
                    .mockMode(false)
                    .build();
        }
        catch (Exception e)
        {
            log.error("Failed to create Razorpay order", e);
            return PaymentResult.builder()
                    .success(false)
                    .message("Error creating order: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public PaymentResult verify(PaymentVerificationDTO dto)
    {
        try
        {
            String data = dto.getRazorpay_order_id() + "|" + dto.getRazorpay_payment_id();
            String generatedSignature = generateHmacSha256Signature(data, razorpayKeySecret);

            if (!generatedSignature.equals(dto.getRazorpay_signature()))
            {
                return PaymentResult.builder()
                        .success(false)
                        .message("Payment signature verification failed")
                        .build();
            }

            log.info("Razorpay payment verified for order: {}", dto.getRazorpay_order_id());

            return PaymentResult.builder()
                    .success(true)
                    .paymentId(dto.getRazorpay_payment_id())
                    .message("Payment verified successfully")
                    .mockMode(false)
                    .build();
        }
        catch (Exception e)
        {
            log.error("Failed to verify Razorpay payment", e);
            return PaymentResult.builder()
                    .success(false)
                    .message("Error verifying payment: " + e.getMessage())
                    .build();
        }
    }

    private String generateHmacSha256Signature(String data, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException
    {
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);
        byte[] hmacData = mac.doFinal(data.getBytes());
        return toHexString(hmacData);
    }

    private String toHexString(byte[] bytes)
    {
        Formatter formatter = new Formatter();
        for (byte b : bytes)
        {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }
}

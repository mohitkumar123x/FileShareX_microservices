package filesharex.payment.service;

import filesharex.payment.document.PaymentTransaction;
import filesharex.payment.dto.PaymentDTO;
import filesharex.payment.dto.PaymentVerificationDTO;
import filesharex.payment.gateway.PaymentGateway;
import filesharex.payment.gateway.PaymentGatewayFactory;
import filesharex.payment.gateway.PaymentResult;
import filesharex.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService
{

    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final RestClient.Builder restClientBuilder;

    @Value("${payment.provider:razorpay}")
    private String paymentProvider;

    @Value("${services.user.base-url}")
    private String userBaseUrl;

    public PaymentDTO createOrder(PaymentDTO paymentDTO, String clerkId)
    {
        PaymentGateway gateway = gatewayFactory.getGateway(paymentProvider);
        PaymentResult result = gateway.createOrder(paymentDTO);

        if (result.isSuccess())
        {
            Map<String, Object> profile = fetchProfile(clerkId);

            PaymentTransaction transaction = PaymentTransaction.builder()
                    .clerkId(clerkId)
                    .orderId(result.getOrderId())
                    .planId(paymentDTO.getPlanId())
                    .amount(paymentDTO.getAmount())
                    .currency(paymentDTO.getCurrency())
                    .status("PENDING")
                    .transactionDate(LocalDateTime.now())
                    .userEmail((String) profile.get("email"))
                    .userName(profile.get("firstName") + " " + profile.get("lastName"))
                    .build();

            paymentTransactionRepository.save(transaction);
            log.info("Order created: {} for user: {}", result.getOrderId(), clerkId);
        }

        return PaymentDTO.builder()
                .orderId(result.getOrderId())
                .success(result.isSuccess())
                .message(result.getMessage())
                .isMock(result.isMockMode())
                .build();
    }

    public PaymentDTO verifyPayment(PaymentVerificationDTO request, String clerkId)
    {
        PaymentGateway gateway = gatewayFactory.getGateway(paymentProvider);
        PaymentResult result = gateway.verify(request);

        if (!result.isSuccess())
        {
            updateTransactionStatus(request.getRazorpay_order_id(), "FAILED", request.getRazorpay_payment_id(), null);
            return PaymentDTO.builder()
                    .success(false)
                    .message(result.getMessage())
                    .build();
        }

        int creditsToAdd = getCreditsForPlan(request.getPlanId());
        String plan = getPlanName(request.getPlanId());

        if (creditsToAdd > 0)
        {
            addCreditsToUser(clerkId, creditsToAdd, plan);
            updateTransactionStatus(request.getRazorpay_order_id(), "SUCCESS", request.getRazorpay_payment_id(), creditsToAdd);

            log.info("Payment verified and credits added for user: {}, credits: {}", clerkId, creditsToAdd);

            return PaymentDTO.builder()
                    .success(true)
                    .message("Payment verified and credits added successfully")
                    .credits(creditsToAdd)
                    .build();
        }
        else
        {
            updateTransactionStatus(request.getRazorpay_order_id(), "FAILED", request.getRazorpay_payment_id(), null);
            return PaymentDTO.builder()
                    .success(false)
                    .message("Invalid plan selected")
                    .build();
        }
    }

    private int getCreditsForPlan(String planId)
    {
        return switch (planId) {
            case "premium" -> 500;
            case "ultimate" -> 5000;
            default -> 0;
        };
    }

    private String getPlanName(String planId)
    {
        return switch (planId) {
            case "premium" -> "PREMIUM";
            case "ultimate" -> "ULTIMATE";
            default -> "BASIC";
        };
    }

    private void addCreditsToUser(String clerkId, int amount, String plan)
    {
        restClientBuilder.build().post()
                .uri(userBaseUrl + "/internal/users/{clerkId}/credits/add", clerkId)
                .body(Map.of("amount", amount, "plan", plan))
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchProfile(String clerkId)
    {
        return restClientBuilder.build().get()
                .uri(userBaseUrl + "/internal/users/{clerkId}", clerkId)
                .retrieve()
                .body(Map.class);
    }

    private void updateTransactionStatus(String orderId, String status, String paymentId, Integer creditsToAdd)
    {
        paymentTransactionRepository.findByOrderId(orderId)
                .ifPresent(transaction -> {
                    transaction.setStatus(status);
                    transaction.setPaymentId(paymentId);
                    if (creditsToAdd != null)
                    {
                        transaction.setCreditsAdded(creditsToAdd);
                    }
                    paymentTransactionRepository.save(transaction);
                });
    }
}

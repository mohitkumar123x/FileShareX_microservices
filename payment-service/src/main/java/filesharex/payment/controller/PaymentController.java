package filesharex.payment.controller;

import filesharex.payment.dto.PaymentDTO;
import filesharex.payment.dto.PaymentVerificationDTO;
import filesharex.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController
{

    private static final String CLERK_ID_HEADER = "X-Clerk-Id";

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @RequestBody PaymentDTO paymentDTO,
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {

        log.info("Create order request from user: {} for plan: {}", clerkId, paymentDTO.getPlanId());
        PaymentDTO response = paymentService.createOrder(paymentDTO, clerkId);

        if (response.getSuccess())
        {
            return ResponseEntity.ok(response);
        }
        else
        {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(
            @RequestBody PaymentVerificationDTO request,
            @RequestHeader(CLERK_ID_HEADER) String clerkId)
    {

        log.info("Verify payment request from user: {} for order: {}", clerkId, request.getRazorpay_order_id());
        PaymentDTO response = paymentService.verifyPayment(request, clerkId);

        if (response.getSuccess())
        {
            return ResponseEntity.ok(response);
        }
        else
        {
            return ResponseEntity.badRequest().body(response);
        }
    }
}

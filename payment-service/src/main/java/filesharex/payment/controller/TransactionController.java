package filesharex.payment.controller;

import filesharex.payment.document.PaymentTransaction;
import filesharex.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController
{

    private static final String CLERK_ID_HEADER = "X-Clerk-Id";

    private final PaymentTransactionRepository paymentTransactionRepository;

    @GetMapping
    public ResponseEntity<?> getUserTransactions(@RequestHeader(CLERK_ID_HEADER) String clerkId)
    {
        log.info("Get transactions request from user: {}", clerkId);
        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByClerkIdAndStatusOrderByTransactionDateDesc(clerkId, "SUCCESS");
        return ResponseEntity.ok(transactions);
    }
}

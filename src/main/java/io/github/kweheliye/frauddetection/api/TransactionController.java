package io.github.kweheliye.frauddetection.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.kweheliye.frauddetection.api.dto.FlaggedResponse;
import io.github.kweheliye.frauddetection.api.dto.SubmitResponse;
import io.github.kweheliye.frauddetection.api.dto.TransactionRequest;
import io.github.kweheliye.frauddetection.api.dto.TransactionResponse;
import io.github.kweheliye.frauddetection.domain.Transaction;
import io.github.kweheliye.frauddetection.service.TransactionQueryService;
import io.github.kweheliye.frauddetection.service.TransactionSubmissionService;
import jakarta.validation.Valid;

/** HTTP layer only: validates input, calls a service, shapes the response. */
@RestController
public class TransactionController {

    private final TransactionSubmissionService submissions;
    private final TransactionQueryService queries;

    public TransactionController(TransactionSubmissionService submissions, TransactionQueryService queries) {
        this.submissions = submissions;
        this.queries = queries;
    }

    @PostMapping("/api/transactions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResponse submit(@Valid @RequestBody TransactionRequest request) {
        Transaction txn = request.toTransaction();
        submissions.submit(txn);
        return SubmitResponse.accepted(txn.transactionId());
    }

    @GetMapping("/api/transactions/{id}")
    public TransactionResponse one(@PathVariable String id) {
        return TransactionResponse.from(queries.get(id));
    }

    @GetMapping("/api/transactions")
    public List<TransactionResponse> recent(@RequestParam(required = false) String customerId,
                                            @RequestParam(defaultValue = "50") int limit) {
        return queries.recent(customerId, limit).stream().map(TransactionResponse::from).toList();
    }

    @GetMapping("/api/flagged")
    public List<FlaggedResponse> flagged(@RequestParam(defaultValue = "50") int limit) {
        return queries.recentFlagged(limit).stream().map(FlaggedResponse::from).toList();
    }
}

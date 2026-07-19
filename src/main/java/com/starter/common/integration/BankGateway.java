package com.starter.common.integration;

import java.math.BigDecimal;

/**
 * A disbursing bank. One implementation per bank; {@link BankRouter} picks the right one. Called
 * only from the outbox processors, outside any transaction (CLAUDE.md §8).
 *
 * <p>Implementations in this template are in-process mocks; a real one injects a
 * {@code @RegisterRestClient} client (with a timeout) and calls it here.
 */
public interface BankGateway {

    /** Stable code identifying this bank (e.g. "HDFC"). */
    String bankCode();

    /** Transfer rail this bank uses for the given anchor (e.g. "IMPS" / "NEFT"). */
    String transferMode(String anchorCode);

    /** Disburse and return the bank reference. Must be idempotent on the payment id. */
    String disburse(Long paymentId, BigDecimal amount);

    /** Settlement status for a reference: "SETTLED" | "FAILED" | anything else = still pending. */
    String checkStatus(String bankReference);
}

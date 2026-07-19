package com.starter.common.integration;

import com.starter.common.exception.BusinessValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Selects the {@link BankGateway} for a disbursement — by anchor (which bank an anchor's payments
 * go through) or by bank code. Discovers all gateways via CDI, so adding a bank is just a new
 * {@code BankGateway} bean; no change here.
 */
@ApplicationScoped
public class BankRouter {

    // Illustrative anchor -> bank routing. Replace with the real mapping/config.
    private static final Map<String, String> ANCHOR_BANK =
            Map.of("TATA", "IDFC", "RELIANCE", "IDFC", "INFOSYS", "HDFC", "WIPRO", "HDFC");

    private final Map<String, BankGateway> byCode;

    @Inject
    public BankRouter(Instance<BankGateway> gateways) {
        this.byCode = gateways.stream().collect(Collectors.toMap(BankGateway::bankCode, g -> g));
    }

    /** Which bank handles this anchor's payments. Unknown anchor is a client-facing 422. */
    public BankGateway resolveByAnchor(String anchorCode) {
        var bankCode = ANCHOR_BANK.get(anchorCode);
        if (bankCode == null) {
            throw new BusinessValidationException("No bank configured for anchor: " + anchorCode);
        }
        return resolveByBank(bankCode);
    }

    /** Gateway for a bank code we already persisted. A miss is a bug, not client input. */
    public BankGateway resolveByBank(String bankCode) {
        var gateway = byCode.get(bankCode);
        if (gateway == null) {
            throw new IllegalStateException("No bank gateway for code: " + bankCode);
        }
        return gateway;
    }
}

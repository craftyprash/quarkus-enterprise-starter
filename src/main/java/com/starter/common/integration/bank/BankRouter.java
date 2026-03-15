package com.starter.common.integration.bank;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@ApplicationScoped
public class BankRouter {

    private final Map<String, BankGateway> gatewaysByCode;

    // anchor code → bank code mapping
    // TODO: move to config or database
    private static final Map<String, String> ANCHOR_BANK_MAP =
            Map.of(
                    "TATA", "IDFC",
                    "RELIANCE", "IDFC",
                    "INFOSYS", "HDFC",
                    "WIPRO", "HDFC");

    @Inject
    public BankRouter(Instance<BankGateway> gateways) {
        this.gatewaysByCode =
                gateways.stream().collect(Collectors.toMap(BankGateway::bankCode, g -> g));
    }

    public BankGateway resolveByAnchor(String anchorCode) {
        var bankCode = ANCHOR_BANK_MAP.get(anchorCode);
        if (bankCode == null) {
            throw new IllegalArgumentException("No bank configured for anchor: " + anchorCode);
        }
        return resolveByBank(bankCode);
    }

    public BankGateway resolveByBank(String bankCode) {
        var gateway = gatewaysByCode.get(bankCode);
        if (gateway == null) {
            throw new NoSuchElementException("Bank gateway not found: " + bankCode);
        }
        return gateway;
    }
}

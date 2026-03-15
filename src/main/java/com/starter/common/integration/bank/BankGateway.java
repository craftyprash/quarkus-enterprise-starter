package com.starter.common.integration.bank;

import java.math.BigDecimal;

/** Strategy interface for bank integrations. Each bank provides its own implementation. */
public interface BankGateway {

    String bankCode();

    String transferMode(String anchorCode);

    String disburse(Long paymentId, BigDecimal amount);

    String checkStatus(String bankReference);
}

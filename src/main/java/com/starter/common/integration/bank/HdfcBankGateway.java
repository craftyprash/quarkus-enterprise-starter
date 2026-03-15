package com.starter.common.integration.bank;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class HdfcBankGateway implements BankGateway {

    private static final Logger log = LoggerFactory.getLogger(HdfcBankGateway.class);

    @Inject @RestClient HdfcBankClient client;

    @Override
    public String bankCode() {
        return "HDFC";
    }

    @Override
    public String transferMode(String anchorCode) {
        return "NEFT";
    }

    @Override
    public String disburse(Long paymentId, BigDecimal amount) {
        var response = client.disburse(new HdfcBankClient.DisburseRequest(paymentId, amount));
        log.info("HDFC disburse payment={} ref={}", paymentId, response.bankReference());
        return response.bankReference();
    }

    @Override
    public String checkStatus(String bankReference) {
        var response = client.checkStatus(bankReference);
        return response.status();
    }
}

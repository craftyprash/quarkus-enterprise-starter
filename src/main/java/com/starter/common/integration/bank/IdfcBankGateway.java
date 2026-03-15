package com.starter.common.integration.bank;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class IdfcBankGateway implements BankGateway {

    private static final Logger log = LoggerFactory.getLogger(IdfcBankGateway.class);

    @Inject @RestClient IdfcBankClient client;

    @Override
    public String bankCode() {
        return "IDFC";
    }

    @Override
    public String transferMode(String anchorCode) {
        return "IMPS";
    }

    @Override
    public String disburse(Long paymentId, BigDecimal amount) {
        var response = client.disburse(new IdfcBankClient.DisburseRequest(paymentId, amount));
        log.info("IDFC disburse payment={} ref={}", paymentId, response.bankReference());
        return response.bankReference();
    }

    @Override
    public String checkStatus(String bankReference) {
        var response = client.checkStatus(bankReference);
        return response.status();
    }
}

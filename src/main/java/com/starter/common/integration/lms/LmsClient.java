package com.starter.common.integration.lms;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.math.BigDecimal;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "lms-api")
@Path("/api/v1")
public interface LmsClient {

    record DisbursementRecord(Long drawdownId, BigDecimal amount, String bankReference) {}

    record DisbursementResponse(String status) {}

    @POST
    @Path("/disbursements")
    DisbursementResponse recordDisbursement(DisbursementRecord request);
}

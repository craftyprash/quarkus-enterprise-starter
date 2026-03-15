package com.starter.common.integration.bank;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.math.BigDecimal;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "idfc-bank-api")
@Path("/api/v1")
public interface IdfcBankClient {

    record DisburseRequest(Long paymentId, BigDecimal amount) {}

    record DisburseResponse(String bankReference, String status) {}

    record StatusResponse(String bankReference, String status) {}

    @POST
    @Path("/disburse")
    DisburseResponse disburse(DisburseRequest request);

    @GET
    @Path("/status")
    StatusResponse checkStatus(@QueryParam("ref") String bankReference);
}

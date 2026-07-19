package com.starter.payment.api;

import com.starter.payment.api.response.PaymentRes;
import com.starter.payment.internal.PaymentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/api/v1/payments")
public class PaymentResource {

    @Inject PaymentService service;

    @GET
    @Path("/{id}")
    public PaymentRes findById(@PathParam("id") Long id) {
        var info = service.findById(id);
        return new PaymentRes(
                info.id(),
                info.drawdownId(),
                info.bank(),
                info.transferMode(),
                info.amount(),
                info.status(),
                info.bankReference(),
                info.createdAt());
    }
}

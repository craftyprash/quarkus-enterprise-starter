package com.starter.drawdown.api;

import com.starter.drawdown.DrawdownApi;
import com.starter.drawdown.api.request.CreateDrawdownReq;
import com.starter.drawdown.api.response.DrawdownRes;
import com.starter.drawdown.internal.DrawdownService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/drawdowns")
public class DrawdownResource {

    @Inject DrawdownService service;

    @POST
    public Response create(@Valid CreateDrawdownReq req) {
        var info =
                service.create(
                        new DrawdownApi.CreateInput(
                                req.applicantId(), req.anchorCode(), req.amount()));
        return Response.status(201).entity(toRes(info)).build();
    }

    @GET
    @Path("/{id}")
    public DrawdownRes findById(@PathParam("id") Long id) {
        return toRes(service.findById(id));
    }

    // State change modelled as a POST on a sub-resource, not an action verb in the path.
    @POST
    @Path("/{id}/disbursement")
    public DrawdownRes disburse(@PathParam("id") Long id) {
        return toRes(service.disburse(id));
    }

    private DrawdownRes toRes(DrawdownApi.Info info) {
        return new DrawdownRes(
                info.id(),
                info.applicantId(),
                info.applicantName(),
                info.anchorCode(),
                info.amount(),
                info.status(),
                info.createdAt());
    }
}

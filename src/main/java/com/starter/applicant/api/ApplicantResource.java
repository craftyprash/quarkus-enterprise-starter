package com.starter.applicant.api;

import com.starter.applicant.api.request.CreateApplicantReq;
import com.starter.applicant.api.response.ApplicantRes;
import com.starter.applicant.internal.ApplicantService;
import com.starter.common.api.PageRes;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/applicants")
public class ApplicantResource {

    @Inject ApplicantService service;

    @POST
    public Response create(@Valid CreateApplicantReq req) {
        var res = service.create(req);
        return Response.status(201).entity(res).build();
    }

    @GET
    @Path("/{id}")
    public ApplicantRes findById(@PathParam("id") Long id) {
        var info = service.findById(id);
        return new ApplicantRes(
                info.id(), info.name(), info.email(), info.status(), info.createdAt());
    }

    @GET
    public PageRes<ApplicantRes> listActive(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("10") @Min(1) @Max(100) int size,
            @QueryParam("sort") @DefaultValue("id") String sort,
            @QueryParam("order") @DefaultValue("asc") String order) {
        var p = service.listActive(page, size, sort, order);
        var content =
                p.content().stream()
                        .map(s -> new ApplicantRes(s.id(), s.name(), null, s.status(), null))
                        .toList();
        return PageRes.of(content, p.page(), p.size(), p.totalElements());
    }
}

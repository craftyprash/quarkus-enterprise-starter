package com.starter.applicant.api;

import com.starter.applicant.api.request.CreateApplicantReq;
import com.starter.applicant.api.response.ApplicantRes;
import com.starter.applicant.internal.ApplicantService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/applicants")
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
    @Path("/active")
    public List<ApplicantRes> listActive() {
        return service.listActive().stream()
                .map(s -> new ApplicantRes(s.id(), s.name(), null, s.status(), null))
                .toList();
    }
}

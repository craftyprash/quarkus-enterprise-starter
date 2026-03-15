package com.starter.common.security;

import com.starter.common.exception.ForbiddenException;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@RequiresPermission(resource = "", action = "")
public class PermissionInterceptor {

    @Inject PermissionContext context;

    @AroundInvoke
    public Object check(InvocationContext ctx) throws Exception {
        var annotation = ctx.getMethod().getAnnotation(RequiresPermission.class);
        if (annotation == null) {
            annotation = ctx.getTarget().getClass().getAnnotation(RequiresPermission.class);
        }

        if (annotation != null && !context.has(annotation.resource(), annotation.action())) {
            throw new ForbiddenException(
                    "Missing permission: " + annotation.resource() + ":" + annotation.action());
        }

        return ctx.proceed();
    }
}

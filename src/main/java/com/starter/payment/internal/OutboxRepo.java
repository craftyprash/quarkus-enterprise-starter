package com.starter.payment.internal;

import com.starter.payment.domain.OutboxEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class OutboxRepo implements PanacheRepository<OutboxEvent> {

    public List<OutboxEvent> findPending(String eventType) {
        return find("status = ?1 and eventType = ?2", "PENDING", eventType).list();
    }
}

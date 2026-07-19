package com.starter.payment.internal;

import com.starter.payment.domain.Payment;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PaymentRepo implements PanacheRepository<Payment> {

    public Optional<Payment> findByDrawdownId(Long drawdownId) {
        return find("drawdownId", drawdownId).firstResultOptional();
    }

    public List<Payment> findByStatus(String status) {
        return find("status", status).list();
    }
}

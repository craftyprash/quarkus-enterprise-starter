package com.starter.payment.internal;

import com.starter.payment.domain.Payment;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class PaymentRepo implements PanacheRepository<Payment> {

    public List<Payment> findByStatus(String status) {
        return find("status", status).list();
    }
}

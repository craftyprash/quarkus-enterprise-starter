package com.starter.drawdown.internal;

import com.starter.drawdown.domain.Drawdown;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DrawdownRepo implements PanacheRepository<Drawdown> {}

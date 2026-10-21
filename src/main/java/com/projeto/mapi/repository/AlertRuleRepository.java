package com.projeto.mapi.repository;

import com.projeto.mapi.model.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByEnabledTrueOrderByIdAsc();

    List<AlertRule> findAllByOrderByIdAsc();
}

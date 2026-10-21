package com.projeto.mapi.repository;

import com.projeto.mapi.model.AlertState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertStateRepository extends JpaRepository<AlertState, AlertState.Key> {
}

package com.projeto.mapi.repository;

import com.projeto.mapi.model.TideTable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TideTableRepository extends JpaRepository<TideTable, Long> {
    Optional<TideTable> findByHarborNameIgnoreCaseAndYear(String harborName, Integer year);
    java.util.List<TideTable> findAllByHarborNameIgnoreCaseAndYear(String harborName, Integer year);
}

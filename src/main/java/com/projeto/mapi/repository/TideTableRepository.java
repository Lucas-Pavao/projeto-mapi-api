package com.projeto.mapi.repository;

import com.projeto.mapi.model.TideTable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TideTableRepository extends JpaRepository<TideTable, Long> {
    Optional<TideTable> findByHarborNameIgnoreCaseAndYear(String harborName, Integer year);
    List<TideTable> findAllByHarborNameIgnoreCaseAndYear(String harborName, Integer year);
    List<TideTable> findAllByStateIgnoreCaseAndYear(String state, Integer year);
    List<TideTable> findAllByHarborNameContainingIgnoreCaseAndYear(String harborName, Integer year);
    
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT t.harborName FROM TideTable t WHERE t.year = :year")
    List<String> findDistinctHarborNamesByYear(Integer year);
}

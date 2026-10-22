package com.projeto.mapi.repository;

import com.projeto.mapi.model.FloodReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloodReportRepository extends JpaRepository<FloodReport, Long> {
    Page<FloodReport> findByStatus(FloodReport.Status status, Pageable pageable);
}

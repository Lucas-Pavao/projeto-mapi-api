package com.projeto.mapi.repository.limpeza;

import com.projeto.mapi.service.limpeza.Central156;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** drainage_cleaning (V40): chamados de limpeza da 156 casados com os pontos. */
@Repository
@RequiredArgsConstructor
public class LimpezaRepository {

    private final JdbcTemplate jdbc;

    /** Insere ou atualiza (a situação e a data da última movimentação mudam entre versões do CSV). Devolve 1 se mudou. */
    public int upsert(Central156.Chamado c, Central156.Casamento m) {
        return jdbc.update("""
                INSERT INTO drainage_cleaning (row_sha256, flood_point_id, group_code, service_code, service_desc, demand_date,
                    last_status_date, status, match_rule, distance_m, source_year)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (row_sha256, flood_point_id) DO UPDATE SET status = EXCLUDED.status,
                    last_status_date = EXCLUDED.last_status_date, ingested_at = now()
                WHERE drainage_cleaning.status IS DISTINCT FROM EXCLUDED.status
                   OR drainage_cleaning.last_status_date IS DISTINCT FROM EXCLUDED.last_status_date""",
                c.rowSha256(), m.ponto().id(), c.groupCode(), c.serviceCode(), c.serviceDesc(), Date.valueOf(c.demandDate()),
                c.lastStatusDate() == null ? null : Date.valueOf(c.lastStatusDate()), c.status(), m.regra(), m.distanciaM(),
                c.sourceYear());
    }

    /** Data da última limpeza feita (situação ATENDIDA) do ponto. */
    public Optional<LocalDate> ultimaLimpeza(long floodPointId) {
        List<Date> d = jdbc.queryForList("""
                SELECT max(last_status_date) FROM drainage_cleaning
                WHERE flood_point_id = ? AND status = 'ATENDIDA' AND last_status_date IS NOT NULL""", Date.class, floodPointId);
        return d.isEmpty() || d.get(0) == null ? Optional.empty() : Optional.of(d.get(0).toLocalDate());
    }

    public List<Map<String, Object>> resumo() {
        return jdbc.queryForList("""
                SELECT f.slug, count(*) AS chamados, count(*) FILTER (WHERE c.status = 'ATENDIDA') AS limpezas,
                       max(c.last_status_date) FILTER (WHERE c.status = 'ATENDIDA') AS ultima_limpeza, max(c.demand_date) AS ultimo_chamado
                FROM drainage_cleaning c JOIN flood_points f ON f.id = c.flood_point_id GROUP BY f.slug ORDER BY f.slug""");
    }
}

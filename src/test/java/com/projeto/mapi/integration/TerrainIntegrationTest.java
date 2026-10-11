package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.dto.TerrainLoadResultDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.UserRepository;
import com.projeto.mapi.service.export.DataExportService;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.service.terrain.TerrainLoadService;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Terreno do PE3D (ADR 0008): migração V9, carga idempotente, endpoint e colunas do export. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "mapi.terrain.dir=src/test/resources/terrain")
class TerrainIntegrationTest extends AbstractTimescaleIntegrationTest {

    private static final Path FIXTURE = Path.of("src/test/resources/terrain");
    private static final String PASSWORD = "SenhaDeTeste#123";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TerrainLoadService loader;
    @Autowired private FloodPointRepository points;
    @Autowired private DataExportService exportService;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    @MockitoBean private TideService tideService;

    @BeforeEach
    void seed() {
        when(tideService.getStoredTideHeightAt(anyDouble(), anyDouble(), any(LocalDateTime.class))).thenReturn(1.0);
        clean();
        point("AV_RECIFE_IBURA", -8.1, -34.95);
        point("CIN_UFPE", -8.05, -34.95);
        point("JABOATAO_CENTRO", -8.11, -35.01);
        point("SEM_CARGA", -8.0, -34.9);
        user("terrain_admin", Role.ADMIN);
        user("terrain_user", Role.USER);
    }

    @AfterEach
    void clean() {
        // FKs para flood_points: não deixar linhas que quebrem deleteAll() de outros testes
        jdbc.execute("TRUNCATE point_terrain_curve, point_terrain, terrain_run RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM flood_points WHERE slug IN ('AV_RECIFE_IBURA','CIN_UFPE','JABOATAO_CENTRO','SEM_CARGA')");
    }

    @Test
    void migracaoV9AplicaECriaTabelasEIndices() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success", String.class))
                .contains("9");
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class)).contains("terrain_run", "point_terrain", "point_terrain_curve");
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'terrain_run'", String.class))
                .contains("terrain_run_one_current_uq", "terrain_run_input_sha256_uq");
    }

    @Test
    void cargaGravaProvenienciaPontosECurva() {
        TerrainLoadResultDTO r = loader.load(FIXTURE);

        assertThat(r.created()).isTrue();
        assertThat(r.pointsOk()).isEqualTo(2);
        assertThat(r.pointsNoData()).isEqualTo(1);
        assertThat(r.curveRows()).isEqualTo(82);
        var run = jdbc.queryForMap("SELECT * FROM terrain_run WHERE id = ?", r.runId());
        assertThat(run.get("vrt_sha256").toString()).startsWith("d63e3b12").endsWith("5408070");
        assertThat(((Number) run.get("flight_year")).intValue()).isEqualTo(2016);
        assertThat(run.get("vertical_reference").toString()).contains("não confirmado").contains("Imbituba");
        assertThat(run.get("gdal_image_digest").toString()).startsWith("sha256:383e17c2");
        assertThat(run.get("current")).isEqualTo(true);
        assertThat(count("SELECT count(*) FROM point_terrain WHERE status = 'NO_DATA' AND street_elevation_m IS NULL"))
                .isEqualTo(1);
        // valores do CSV de origem (cota_rua_m_raio3m = 1.392 no Ibura)
        assertThat(jdbc.queryForObject("""
                SELECT street_elevation_m FROM point_terrain pt JOIN flood_points f ON f.id = pt.flood_point_id
                WHERE f.slug = 'AV_RECIFE_IBURA'""", Double.class)).isEqualTo(1.392);
        assertThat(count("SELECT count(*) FROM point_terrain_curve WHERE m3_per_cm IS NULL")).isEqualTo(2);
    }

    @Test
    void duasCargasDoMesmoCsvGeramUmSoRun() {
        TerrainLoadResultDTO first = loader.load(FIXTURE);
        TerrainLoadResultDTO second = loader.load(FIXTURE);

        assertThat(second.created()).isFalse();
        assertThat(second.runId()).isEqualTo(first.runId());
        assertThat(second.current()).isTrue();
        assertThat(count("SELECT count(*) FROM terrain_run")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM point_terrain")).isEqualTo(3);
    }

    @Test
    void novaCargaTrocaOVigenteEMantemHistorico(@TempDir Path tmp) throws Exception {
        TerrainLoadResultDTO first = loader.load(FIXTURE);
        for (String f : List.of("ponto_terreno.csv", "ponto_terreno.meta.json", "curva_cota_volume.csv")) {
            Files.copy(FIXTURE.resolve(f), tmp.resolve(f));
        }
        // revalidação mudou a área contribuinte do Ibura
        Path csv = tmp.resolve("ponto_terreno.csv");
        Files.writeString(csv, Files.readString(csv).replace(",573046,52,52,", ",111111,52,52,"));

        TerrainLoadResultDTO second = loader.load(tmp);

        assertThat(second.created()).isTrue();
        assertThat(second.runId()).isNotEqualTo(first.runId());
        assertThat(count("SELECT count(*) FROM terrain_run")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM terrain_run WHERE current")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id FROM terrain_run WHERE current", Long.class)).isEqualTo(second.runId());
        mockMvc_get("AV_RECIFE_IBURA", "terrain_user")
                .andExpect(jsonPath("$.provenanceId").value(second.runId()))
                .andExpect(jsonPath("$.contributingAreaM2").value(111111.0));
    }

    @Test
    void endpointRetornaTerrenoOk() throws Exception {
        TerrainLoadResultDTO r = loader.load(FIXTURE);

        mockMvc_get("AV_RECIFE_IBURA", "terrain_user")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.provenanceId").value(r.runId()))
                .andExpect(jsonPath("$.streetElevationM").value(1.392))
                .andExpect(jsonPath("$.meanSlopePct").value(6.244))
                .andExpect(jsonPath("$.depressionDepthM").value(0.098))
                .andExpect(jsonPath("$.contributingAreaM2").value(573046.0))
                .andExpect(jsonPath("$.volumeCurve.length()").value(41))
                .andExpect(jsonPath("$.volumeCurve[0].aboveBottomCm").value(0))
                .andExpect(jsonPath("$.volumeCurve[40].aboveBottomCm").value(200))
                .andExpect(jsonPath("$.provenance.flightYear").value(2016))
                .andExpect(jsonPath("$.provenance.verticalReference").value(org.hamcrest.Matchers.containsString("não confirmado")))
                .andExpect(jsonPath("$.diagnostics.basinTouchesWindowEdge").value(false))
                .andExpect(jsonPath("$.tideComparabilityWarning").value(org.hamcrest.Matchers.containsString("NÃO é comparável")));
    }

    @Test
    void endpointRetornaNoDataComAtributosNulos() throws Exception {
        loader.load(FIXTURE);

        mockMvc_get("JABOATAO_CENTRO", "terrain_user")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_DATA"))
                .andExpect(jsonPath("$.streetElevationM").doesNotExist())
                .andExpect(jsonPath("$.volumeCurve.length()").value(0))
                .andExpect(jsonPath("$.tideComparabilityWarning").exists());
        // ponto que existe mas não estava no CSV
        mockMvc_get("SEM_CARGA", "terrain_user").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_DATA"));
    }

    @Test
    void endpointRetorna404ParaPontoInexistente() throws Exception {
        mockMvc_get("NAO_EXISTE", "terrain_user").andExpect(status().isNotFound());
    }

    @Test
    void postAdminExigeAdminECriaDepoisReaproveita() throws Exception {
        mockMvc.perform(post("/api/admin/terrain/runs").header("Authorization", "Bearer " + token("terrain_user")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/terrain/runs").header("Authorization", "Bearer " + token("terrain_admin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.pointsOk").value(2))
                .andExpect(jsonPath("$.pointsNoData").value(1));
        mockMvc.perform(post("/api/admin/terrain/runs").header("Authorization", "Bearer " + token("terrain_admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));
        assertThat(count("SELECT count(*) FROM terrain_run")).isEqualTo(1);
    }

    @Test
    void cargaComArquivoAusenteDa400(@TempDir Path empty) {
        org.junit.jupiter.api.Assertions.assertThrows(com.projeto.mapi.exception.InvalidRequestException.class,
                () -> loader.load(empty));
        assertThat(count("SELECT count(*) FROM terrain_run")).isZero();
    }

    @Test
    void exportTemColunasTerrainEHashDoVrtNoManifesto() {
        TerrainLoadResultDTO r = loader.load(FIXTURE);

        var export = exportService.exportCsv("all", 1, LocalDateTime.now());

        String[] lines = export.csv().split("\n");
        String[] header = lines[0].split(",", -1);
        assertThat(lines[0]).endsWith(",terrain_run_id,terrain_street_elevation_m,terrain_mean_slope_pct,"
                + "terrain_depression_depth_m,terrain_contributing_area_m2");
        int elev = List.of(header).indexOf("terrain_street_elevation_m");
        var ibura = java.util.Arrays.stream(lines).skip(1).filter(l -> l.startsWith("AV_RECIFE_IBURA,")).findFirst().orElseThrow();
        var jaboatao = java.util.Arrays.stream(lines).skip(1).filter(l -> l.startsWith("JABOATAO_CENTRO,")).findFirst().orElseThrow();
        var semCarga = java.util.Arrays.stream(lines).skip(1).filter(l -> l.startsWith("SEM_CARGA,")).findFirst().orElseThrow();
        assertThat(ibura.split(",", -1)).hasSameSizeAs(header);
        assertThat(ibura.split(",", -1)[elev]).isEqualTo("1.392");
        assertThat(ibura.split(",", -1)[elev - 1]).isEqualTo(String.valueOf(r.runId()));
        assertThat(jaboatao.split(",", -1)[elev]).isEmpty();
        assertThat(semCarga.split(",", -1)[elev]).isEmpty();
        assertThat(export.manifest().terrain().runId()).isEqualTo(r.runId());
        assertThat(export.manifest().terrain().vrtSha256()).startsWith("d63e3b12");
        assertThat(export.manifest().datasetId()).isEqualTo(com.projeto.mapi.service.ingestion.Hashing.sha256(export.csv()));
    }

    @Test
    void exportSemCargaTemColunasVaziasEManifestoSemTerreno() {
        var export = exportService.exportCsv("all", 1, LocalDateTime.now());
        assertThat(export.csv().split("\n")[0]).contains("terrain_street_elevation_m");
        assertThat(export.manifest().terrain()).isNull();
    }

    // --- util ---

    private org.springframework.test.web.servlet.ResultActions mockMvc_get(String slug, String user) {
        try {
            return mockMvc.perform(get("/api/pontos/" + slug + "/terrain").header("Authorization", "Bearer " + token(user)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final java.util.Map<String, String> TOKENS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Login tem rate limit (429): um token por usuário na classe. */
    private String token(String username) throws Exception {
        String cached = TOKENS.get(username);
        if (cached != null) return cached;
        String t = login(username);
        TOKENS.put(username, t);
        return t;
    }

    private String login(String username) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("username", username, "password", PASSWORD));
        String resp = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).get("accessToken").asText();
    }

    private void user(String name, Role role) {
        if (users.findByUsername(name).isEmpty()) {
            users.save(User.builder().username(name).password(encoder.encode(PASSWORD)).role(role).build());
        }
    }

    private void point(String slug, double lat, double lon) {
        points.save(FloodPoint.builder().slug(slug).name(slug).latitude(lat).longitude(lon).build());
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}

package com.projeto.mapi.service.radar;

import com.projeto.mapi.config.RadarProperties;
import com.projeto.mapi.repository.radar.RadarImageRepository;
import com.projeto.mapi.repository.radar.RadarImageRepository.Entry;
import com.projeto.mapi.service.radar.impl.ApacRadarHttpClientTestAccess;
import com.projeto.mapi.service.radar.impl.RadarArchiveServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RadarArchiveServiceImplTest {

    @TempDir
    Path dir;

    private RadarProperties props() {
        RadarProperties p = new RadarProperties();
        p.setDirectory(dir.toString());
        p.setProducts(List.of("cappi1km"));
        p.setMinIntervalMs(0);
        return p;
    }

    @Test
    void listagemApacheDevolveSoOsPngsComInstante() {
        String html = "<a href=\"201810311740.PPI_top.nc\">x</a><a href=\"202611171155.cappi1km_trans.png\">y</a>"
                + "<a href=\"202611171155.Acum1h_trans.png\">z</a><a href=\"?C=N;O=D\">Name</a>";
        assertThat(ApacRadarHttpClientTestAccess.parse(html))
                .containsExactly("202611171155.cappi1km_trans.png", "202611171155.Acum1h_trans.png");
    }

    @Test
    void gravaArquivoComHashEMarcaAusenteOInstanteQueFaltaNaListagem() throws Exception {
        ApacRadarClient client = mock(ApacRadarClient.class);
        RadarImageRepository repo = mock(RadarImageRepository.class);
        when(repo.stored(anyString(), any())).thenReturn(Set.of());
        when(repo.known(anyString(), any())).thenReturn(Set.of());
        // 11:05 e 11:25 na listagem; 11:15 falta
        when(client.listFiles()).thenReturn(List.of("202611171105.cappi1km_trans.png", "202611171125.cappi1km_trans.png",
                "202611171105.Acum1h_trans.png"));
        when(client.fetch("202611171105.cappi1km_trans.png")).thenReturn(Optional.of(new byte[]{1, 2, 3}));
        when(client.fetch("202611171125.cappi1km_trans.png")).thenReturn(Optional.empty());   // 404
        Clock clock = Clock.fixed(Instant.parse("2026-11-17T12:05:00Z"), ZoneOffset.UTC);

        RadarArchiveService.Result r = new RadarArchiveServiceImpl(client, repo, props(), clock).collect();

        assertThat(r.saved()).isEqualTo(1);
        assertThat(r.absent()).isEqualTo(2);           // 11:25 com 404 e 11:15 fora da listagem
        assertThat(Files.readAllBytes(dir.resolve("2026/11/17/202611171105.cappi1km_trans.png"))).containsExactly(1, 2, 3);
        ArgumentCaptor<Entry> cap = ArgumentCaptor.forClass(Entry.class);
        verify(repo, times(3)).upsert(cap.capture());
        Entry ok = cap.getAllValues().stream().filter(e -> e.status().equals("OK")).findFirst().orElseThrow();
        assertThat(ok.tsUtc()).isEqualTo(Instant.parse("2026-11-17T11:05:00Z"));   // nome do arquivo em UTC
        assertThat(ok.sha256()).isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        assertThat(cap.getAllValues()).extracting(Entry::status).containsExactlyInAnyOrder("OK", "ABSENT", "ABSENT");
        verify(client, never()).fetch("202611171105.Acum1h_trans.png");             // produto não configurado
    }

    @Test
    void arquivoJaPresenteNoVolumeNaoEBaixadoDeNovo() throws Exception {
        ApacRadarClient client = mock(ApacRadarClient.class);
        RadarImageRepository repo = mock(RadarImageRepository.class);
        when(repo.stored(anyString(), any())).thenReturn(Set.of());
        when(repo.known(anyString(), any())).thenReturn(Set.of());
        when(client.listFiles()).thenReturn(List.of("202611171105.cappi1km_trans.png"));
        Path f = dir.resolve("2026/11/17/202611171105.cappi1km_trans.png");
        Files.createDirectories(f.getParent());
        Files.write(f, new byte[]{9});
        Clock clock = Clock.fixed(Instant.parse("2026-11-17T11:20:00Z"), ZoneOffset.UTC);

        RadarArchiveService.Result r = new RadarArchiveServiceImpl(client, repo, props(), clock).collect();

        assertThat(r.saved()).isEqualTo(1);
        verify(client, never()).fetch(anyString());
    }

    @Test
    void listagemForaDoArNaoGravaNadaEMarcaFonteIndisponivel() {
        ApacRadarClient client = mock(ApacRadarClient.class);
        RadarImageRepository repo = mock(RadarImageRepository.class);
        when(client.listFiles()).thenThrow(new com.projeto.mapi.exception.SourceUnavailableException("fora"));
        RadarArchiveService.Result r = new RadarArchiveServiceImpl(client, repo, props(), Clock.systemUTC()).collect();
        assertThat(r.sourceUnavailable()).isTrue();
        verify(repo, never()).upsert(any());
    }
}

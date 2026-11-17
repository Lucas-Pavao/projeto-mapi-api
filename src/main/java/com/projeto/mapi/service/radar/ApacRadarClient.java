package com.projeto.mapi.service.radar;

import java.util.List;
import java.util.Optional;

/** Acesso HTTP ao diretório de imagens do radar da APAC. */
public interface ApacRadarClient {

    /** Nomes de arquivo da listagem do diretório. */
    List<String> listFiles();

    /** Bytes do arquivo; vazio se 404. Outros erros lançam SourceUnavailableException. */
    Optional<byte[]> fetch(String fileName);
}

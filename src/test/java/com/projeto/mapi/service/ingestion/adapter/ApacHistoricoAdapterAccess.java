package com.projeto.mapi.service.ingestion.adapter;

/** Acesso de teste aos auxiliares package-private do adaptador da APAC histórica. */
public final class ApacHistoricoAdapterAccess {
    private ApacHistoricoAdapterAccess() {
    }

    public static Double number(String t) {
        return ApacHistoricoAdapter.number(t);
    }
}

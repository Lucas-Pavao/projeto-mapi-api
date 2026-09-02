package com.projeto.mapi.service.flood;

public interface CivilDefenseService {
    void ingestFloodEvents(String resourceId);
    void ingestLastYears(int years);
}

package com.projeto.mapi.service;

public interface CivilDefenseService {
    void ingestFloodEvents(String resourceId);
    void ingestLastYears(int years);
}

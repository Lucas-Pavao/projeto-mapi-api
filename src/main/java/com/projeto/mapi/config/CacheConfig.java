package com.projeto.mapi.config;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableCaching
@EnableScheduling
public class CacheConfig {

    // F2-BACK-17: tideNearestHarbor e tideTableDaily NAO sao mais despejados a cada 10 min: a tabua de mare de um
    // dia e a busca do porto mais proximo sao imutaveis; despeja-los fazia ~1 em cada 10 exports de 5 anos refazer
    // ~1.800 chamadas externas (35-49 s). Respostas de erro da API nao entram no cache (unless em TabuaMareServiceImpl).
    // Evict all entries from caches every 10 minutes to prevent stale data.
    // Keep this list in sync with the actual @Cacheable value names in the codebase:
    // marineData (MarineServiceImpl), weatherData (WeatherServiceImpl),
    // tideDataLocal (TideServiceImpl), tideDataExternal (TabuaMareServiceImpl),
    // tideNearestHarbor / tideTableDaily (TabuaMareServiceImpl - external lookups reused across
    // every hour of a given day/coordinate by exportUnifiedDataWithAccumulated),
    // floodPoints (SensorServiceImpl#getFloodPointsCache - also evicted on demand via
    // MapiServiceImpl#createFloodPoint, but included here too since repairStationMappings/
    // syncSensors mutate FloodPoint rows without evicting this cache).
    @CacheEvict(value = {"marineData", "weatherData", "tideDataLocal", "tideDataExternal", "floodPoints"}, allEntries = true)
    @Scheduled(fixedRate = 600000)
    public void evictAllCaches() {
        // Scheduled task to clear caches
    }
}

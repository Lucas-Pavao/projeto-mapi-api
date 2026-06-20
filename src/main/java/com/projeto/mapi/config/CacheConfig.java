package com.projeto.mapi.config;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class CacheConfig {

    // Evict all entries from caches every 10 minutes to prevent stale data
    @CacheEvict(value = {"marineData", "weatherData", "tideData", "floodPrediction", "sensorDistinctIds", "sensorInventory"}, allEntries = true)
    @Scheduled(fixedRate = 600000)
    public void evictAllCaches() {
        // Scheduled task to clear caches
    }
}

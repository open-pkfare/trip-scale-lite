package com.pkfare.trip.scale.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock数据缓存管理器
 * 在应用启动时预加载所有mock数据到内存中，避免重复的文件读取和JSON解析
 * 
 * @author Trip Scale Team
 */
@Slf4j
@Component
public class MockDataCacheManager {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 使用ConcurrentHashMap缓存所有mock数据的JsonNode
    private final ConcurrentHashMap<String, JsonNode> mockDataCache = new ConcurrentHashMap<>();
    
    // 缓存状态标记
    private volatile boolean cacheInitialized = false;
    
    /**
     * 应用启动时预加载所有mock数据
     */
    @PostConstruct
    public void initializeCache() {
        long startTime = System.currentTimeMillis();
        log.info("Starting mock data cache initialization...");
        
        try {
            // 预加载各种mock数据文件
            loadMockDataFile("activities", "mock/activities-mock.json");
            loadMockDataFile("flights", "mock/flights-mock.json");
            loadMockDataFile("hotels", "mock/hotels-mock.json");
            loadMockDataFile("hotelsByCity", "mock/hotelofcity/hotel-of-city.json");
            loadMockDataFile("locations", "mock/locations/locations.json");
            loadMockDataFile("flightDates-FCO-SZX", "mock/flightdates/FCO-SZX.json");
            loadMockDataFile("flightDates-SZX-FCO", "mock/flightdates/SZX-FCO.json");
            
            cacheInitialized = true;
            long duration = System.currentTimeMillis() - startTime;
            log.info("Mock data cache initialization completed in {} ms", duration);
            logCacheStatistics();
            
        } catch (Exception e) {
            log.error("Failed to initialize mock data cache", e);
            cacheInitialized = false;
        }
    }
    
    /**
     * 加载单个mock数据文件到缓存
     */
    private void loadMockDataFile(String cacheKey, String filePath) {
        try {
            ClassPathResource resource = new ClassPathResource(filePath);
            if (resource.exists()) {
                JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
                mockDataCache.put(cacheKey, rootNode);
                log.info("Loaded mock data file: {} -> cache key: {}", filePath, cacheKey);
            } else {
                log.warn("Mock data file not found: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to load mock data file: {}", filePath, e);
        }
    }
    
    /**
     * 获取缓存的mock数据
     * 
     * @param cacheKey 缓存键
     * @return JsonNode 原始JSON数据，如果未找到则返回null
     */
    public JsonNode getCachedMockData(String cacheKey) {
        if (!cacheInitialized) {
            log.warn("Cache not initialized, returning null for key: {}", cacheKey);
            return null;
        }
        
        JsonNode cachedData = mockDataCache.get(cacheKey);
        if (cachedData == null) {
            log.warn("No cached data found for key: {}", cacheKey);
        }
        
        return cachedData;
    }
    
    /**
     * 获取缓存的mock数据的data节点
     * 
     * @param cacheKey 缓存键
     * @return JsonNode data节点，如果未找到则返回null
     */
    public JsonNode getCachedMockDataArray(String cacheKey) {
        JsonNode rootNode = getCachedMockData(cacheKey);
        if (rootNode != null) {
            JsonNode dataNode = rootNode.get("data");
            if (dataNode != null && dataNode.isArray()) {
                return dataNode;
            } else {
                log.warn("Data node not found or not array for cache key: {}", cacheKey);
            }
        }
        return null;
    }
    
    /**
     * 检查缓存是否已初始化
     */
    public boolean isCacheInitialized() {
        return cacheInitialized;
    }
    
    /**
     * 记录缓存统计信息
     */
    private void logCacheStatistics() {
        log.info("=== Mock Data Cache Statistics ===");
        mockDataCache.forEach((key, value) -> {
            JsonNode dataNode = value.get("data");
            int size = (dataNode != null && dataNode.isArray()) ? dataNode.size() : 0;
            log.info("Cache key: {} -> {} items", key, size);
        });
        log.info("Total cache keys: {}", mockDataCache.size());
        log.info("Cache Status: {}", cacheInitialized ? "INITIALIZED" : "NOT_INITIALIZED");
    }
    
    /**
     * 手动刷新缓存（用于开发和测试）
     */
    public void refreshCache() {
        log.info("Manually refreshing mock data cache...");
        mockDataCache.clear();
        cacheInitialized = false;
        initializeCache();
    }
    
    /**
     * 获取所有可用的缓存键
     */
    public java.util.Set<String> getAvailableCacheKeys() {
        return mockDataCache.keySet();
    }
}

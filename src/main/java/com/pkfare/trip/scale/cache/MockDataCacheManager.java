package com.pkfare.trip.scale.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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
            loadMockDataFile( "mock/activities/*.json");
            loadMockDataFile( "mock/flights-mock.json");
            loadMockDataFile("mock/hotels/*.json");
            loadMockDataFile( "mock/hotelofcity/*.json");
            loadMockDataFile( "mock/locations/locations.json");
            loadMockDataFile( "mock/hotelbygeocode/*.json");

            // 使用通配符加载所有flightdates文件
            loadMockDataFile( "mock/flightdates/*.json");
            
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
     * 加载mock数据文件到缓存，支持通配符*.json
     */
    private void loadMockDataFile( String filePath) {
        try {
            // 检查是否包含通配符
            if (filePath.contains("*.json")) {
                loadMockDataFilesWithWildcard(filePath);
            } else {
                loadSingleMockDataFile(filePath);
            }
        } catch (Exception e) {
            log.error("Failed to load mock data file(s): {}", filePath, e);
        }
    }
    
    /**
     * 加载单个mock数据文件
     */
    private void loadSingleMockDataFile(String filePath) {
        try {
            ClassPathResource resource = new ClassPathResource(filePath);
            if (resource.exists()) {
                JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
                mockDataCache.put(filePath, rootNode);
                log.info("Loaded mock data file: {}", filePath);
            } else {
                log.warn("Mock data file not found: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to load mock data file: {}", filePath, e);
        }
    }
    
    /**
     * 使用通配符加载多个mock数据文件
     */
    private void loadMockDataFilesWithWildcard(String wildcardPath) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            
            // 将通配符路径转换为classpath格式
            String classpathPattern = "classpath:" + wildcardPath;
            Resource[] resources = resolver.getResources(classpathPattern);
            
            log.info("Found {} files matching pattern: {}", resources.length, wildcardPath);
            
            for (Resource resource : resources) {
                if (resource.exists() && resource.isReadable()) {
                    try {
                        JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
                        
                        // 从文件名生成缓存键
                        String filename = resource.getFilename();
                        // 将通配符路径转换为具体文件路径
                        String individualCacheKey = wildcardPath.replace("*.json", filename != null ? filename : "unknown");
                        
                        mockDataCache.put(individualCacheKey, rootNode);
                        log.info("Loaded mock data  cache key: {}", individualCacheKey);
                        
                    } catch (IOException e) {
                        log.error("Failed to load mock data file: {}", resource.getURI(), e);
                    }
                }
            }
            
            if (resources.length == 0) {
                log.warn("No files found matching pattern: {}", wildcardPath);
            }
            
        } catch (IOException e) {
            log.error("Failed to resolve wildcard pattern: {}", wildcardPath, e);
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
    
    /**
     * 根据文件名获取flightdates的缓存数据
     * 
     * @param filename 文件名，如 "SZX-FCO" 或 "SZX-FCO.json"
     * @return JsonNode 对应的JSON数据，如果未找到则返回null
     */
    public JsonNode getFlightDatesMockData(String filename) {
        // 确保文件名有.json后缀
        String fullFilename = filename.endsWith(".json") ? filename : filename + ".json";
        String cacheKey = "mock/flightdates/" + fullFilename;
        return getCachedMockData(cacheKey);
    }
    
    /**
     * 根据文件名获取flightdates的data数组
     * 
     * @param filename 文件名，如 "SZX-FCO" 或 "SZX-FCO.json"
     * @return JsonNode data数组，如果未找到则返回null
     */
    public JsonNode getFlightDatesMockDataArray(String filename) {
        JsonNode rootNode = getFlightDatesMockData(filename);
        if (rootNode != null) {
            JsonNode dataNode = rootNode.get("data");
            if (dataNode != null && dataNode.isArray()) {
                return dataNode;
            } else {
                log.warn("Data node not found or not array for flightdates file: {}", filename);
            }
        }
        return null;
    }
    
    /**
     * 根据坐标获取activities的缓存数据
     * 
     * @param latitude 纬度
     * @param longitude 经度
     * @return JsonNode 对应的JSON数据，如果未找到则返回null
     */
    public JsonNode getActivitiesMockData(double latitude, double longitude) {
        String filename = latitude + "-" + longitude + ".json";
        String cacheKey = "mock/activities/" + filename;
        return getCachedMockData(cacheKey);
    }
    
    /**
     * 根据坐标获取activities的data数组
     * 
     * @param latitude 纬度
     * @param longitude 经度
     * @return JsonNode data数组，如果未找到则返回null
     */
    public JsonNode getActivitiesMockDataArray(double latitude, double longitude) {
        JsonNode rootNode = getActivitiesMockData(latitude, longitude);
        if (rootNode != null) {
            JsonNode dataNode = rootNode.get("data");
            if (dataNode != null && dataNode.isArray()) {
                return dataNode;
            } else {
                log.warn("Data node not found or not array for activities file: {}-{}", latitude, longitude);
            }
        }
        return null;
    }
}

package com.pkfare.trip.scale.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock数据处理抽象基类
 * 提供通用的缓存优化功能，支持缓存回退到文件读取
 * 
 * @author Trip Scale Team
 */
@Slf4j
public abstract class AbstractMockDataProcessor {
    
    @Autowired
    protected MockDataCacheManager mockDataCacheManager;
    
    protected final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 获取Mock数据的data数组，优先从缓存获取，缓存未命中时回退到文件读取
     * 
     * @param cacheKey 缓存键
     * @param fallbackFilePath 回退文件路径
     * @return JsonNode data数组
     */
    protected JsonNode getMockDataArray(String cacheKey, String fallbackFilePath) {
        long startTime = System.currentTimeMillis();
        
        // 优先尝试从缓存获取
        if (mockDataCacheManager.isCacheInitialized()) {
            JsonNode cachedData = mockDataCacheManager.getCachedMockData(cacheKey);
            if (cachedData != null) {
                JsonNode dataNode = cachedData.get("data");
                if (dataNode != null && dataNode.isArray()) {
                    long duration = System.currentTimeMillis() - startTime;
                    log.debug("Cache hit for key: {} in {} ms", cacheKey, duration);
                    return dataNode;
                } else {
                    log.warn("Cached data format is invalid for key: {}, falling back to file reading", cacheKey);
                }
            } else {
                log.debug("Cache miss for key: {}, falling back to file reading", cacheKey);
            }
        } else {
            log.debug("Cache not initialized, falling back to file reading for key: {}", cacheKey);
        }
        
        // 缓存未命中，回退到文件读取
        return readMockDataFromFile(fallbackFilePath);
    }
    
    /**
     * 从文件读取Mock数据
     * 
     * @param filePath 文件路径
     * @return JsonNode data数组
     */
    private JsonNode readMockDataFromFile(String filePath) {
        try {
            ClassPathResource resource = new ClassPathResource(filePath);
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            JsonNode dataNode = rootNode.get("data");
            
            if (dataNode == null || !dataNode.isArray()) {
                log.warn("Mock data format is invalid in file: {}, returning null", filePath);
                return null;
            }
            
            log.debug("Successfully read mock data from file: {}", filePath);
            return dataNode;
            
        } catch (IOException e) {
            log.error("Failed to read mock data from file: {}", filePath, e);
            return null;
        }
    }
    
    /**
     * 解析Mock数据数组为DTO列表的通用方法
     * 
     * @param dataArray JSON数据数组
     * @param parser 单个数据项解析器
     * @param <T> DTO类型
     * @return DTO列表
     */
    protected <T> List<T> parseMockDataArray(JsonNode dataArray, MockDataItemParser<T> parser) {
        List<T> result = new ArrayList<>();
        
        if (dataArray == null || !dataArray.isArray()) {
            return result;
        }
        
        for (JsonNode itemNode : dataArray) {
            try {
                T dto = parser.parse(itemNode);
                if (dto != null) {
                    result.add(dto);
                }
            } catch (Exception e) {
                log.error("Failed to parse mock data item", e);
            }
        }
        
        return result;
    }
    
    /**
     * Mock数据项解析器接口
     */
    @FunctionalInterface
    public interface MockDataItemParser<T> {
        T parse(JsonNode itemNode) throws Exception;
    }
    
    /**
     * 辅助方法：安全获取字符串值
     */
    protected String getStringValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : null;
    }
    
    /**
     * 辅助方法：安全获取布尔值
     */
    protected Boolean getBooleanValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asBoolean() : null;
    }
    
    /**
     * 辅助方法：安全获取整数值
     */
    protected Integer getIntValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asInt() : null;
    }
    
    /**
     * 辅助方法：安全获取双精度值
     */
    protected Double getDoubleValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asDouble() : null;
    }
}

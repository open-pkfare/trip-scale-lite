# Trip Scale Lite - 单元测试指南

## 测试结构

本项目包含完整的单元测试、集成测试和端到端测试，覆盖了旅行计划生成系统的所有核心功能。

### 测试分类

#### 1. 服务层测试 (`service/plan/`)
- **GeneratePlanServiceTest**: 主服务类测试，验证完整的旅行计划生成流程
- **FlightSearchServiceTest**: 航班搜索服务测试，包括精确时间和灵活时间搜索
- **HotelSearchServiceTest**: 酒店搜索服务测试，验证按城市搜索和价格筛选
- **ActivitySearchServiceTest**: 活动搜索服务测试，验证基于位置的活动筛选
- **PlanAggregationServiceTest**: 计划聚合服务测试，验证结果整合和每日行程构建

#### 2. 外部服务测试 (`service/external/`)
- **AmadeusFlightServiceTest**: Amadeus航班API集成测试，包括重试机制验证

#### 3. 工具类测试 (`util/`)
- **ValidationUtilTest**: 参数验证工具测试，覆盖所有验证规则
- **DateUtilTest**: 日期工具测试，验证日期解析、格式化和计算
- **PriceUtilTest**: 价格工具测试，验证价格解析、计算和格式化
- **LocationUtilTest**: 位置工具测试，验证距离计算和范围判断

#### 4. 控制器测试 (`controller/`)
- **TripPlanControllerTest**: REST API控制器测试，验证HTTP请求处理

#### 5. 集成测试 (`integration/`)
- **TripPlanIntegrationTest**: 端到端集成测试，验证完整的请求-响应流程

## 运行测试

### 运行所有测试
```bash
mvn test
```

### 运行特定测试类
```bash
mvn test -Dtest=GeneratePlanServiceTest
```

### 运行测试套件
```bash
mvn test -Dtest=TripScaleTestSuite
```

### 运行特定测试方法
```bash
mvn test -Dtest=GeneratePlanServiceTest#testGeneratePlan_Success
```

### 生成测试报告
```bash
mvn surefire-report:report
```

## 测试配置

### 测试环境配置
测试使用独立的配置文件 `application-test.yml`，包含：
- 减少的重试次数和超时时间
- 测试用的API配置
- 调试级别的日志配置

### Mock策略
- **外部API调用**: 使用Mockito模拟Amadeus API响应
- **AI服务**: 模拟Gemini AI服务调用
- **数据库操作**: 当前版本不涉及数据库，未来可扩展

## 测试覆盖率

### 核心功能覆盖
- ✅ 参数验证 (100%)
- ✅ 航班搜索 (精确时间/灵活时间, 往返/单程)
- ✅ 酒店搜索 (按城市搜索, 价格筛选)
- ✅ 活动搜索 (基于位置, 评分筛选)
- ✅ 计划聚合 (费用计算, 状态判断)
- ✅ 异常处理 (API异常, 参数异常, 预算超支)

### 边界条件测试
- ✅ 空输入处理
- ✅ 无效参数处理
- ✅ API异常重试
- ✅ 预算限制验证
- ✅ 日期范围验证

### 性能测试
- ✅ 大数据量请求处理
- ✅ 并发请求模拟
- ✅ 超时处理验证

## 测试数据

### Mock数据策略
- **航班数据**: 模拟Amadeus FlightDate和FlightOfferSearch响应
- **酒店数据**: 模拟Hotel和HotelOfferSearch响应
- **活动数据**: 模拟Activity响应，包含位置和评分信息
- **AI响应**: 模拟Gemini生成的旅行计划文本

### 测试场景
1. **成功场景**: 完整的旅行计划生成流程
2. **失败场景**: API异常、参数错误、预算超支
3. **边界场景**: 极端参数值、空数据、大数据量
4. **业务场景**: 不同的行程类型（往返/单程、精确/灵活时间）

## 持续集成

### GitHub Actions配置
```yaml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 21
        uses: actions/setup-java@v2
        with:
          java-version: '21'
      - name: Run tests
        run: mvn test
      - name: Generate test report
        run: mvn surefire-report:report
```

## 调试测试

### 启用调试日志
在测试中添加：
```java
@TestPropertySource(properties = {
    "logging.level.com.pkfare.trip.scale=DEBUG"
})
```

### 查看Mock调用
```java
verify(mockService, times(1)).method(any());
verifyNoMoreInteractions(mockService);
```

### 断言技巧
```java
// 验证集合大小
assertThat(result).hasSize(expectedSize);

// 验证对象属性
assertThat(result.getStatus()).isEqualTo(PlanStatus.SUCCESS);

// 验证异常
assertThatThrownBy(() -> service.method())
    .isInstanceOf(ParameterValidationException.class)
    .hasMessageContaining("expected message");
```

## 最佳实践

1. **测试命名**: 使用 `test[MethodName]_[Scenario]_[ExpectedResult]` 格式
2. **Given-When-Then**: 清晰的测试结构
3. **Mock最小化**: 只Mock必要的外部依赖
4. **数据隔离**: 每个测试使用独立的测试数据
5. **异常测试**: 验证异常情况和错误处理
6. **边界测试**: 测试极端值和边界条件

## 故障排除

### 常见问题
1. **Mock不生效**: 检查@MockBean和@Mock的使用
2. **测试超时**: 调整测试环境的超时配置
3. **序列化问题**: 检查JSON序列化配置
4. **依赖注入失败**: 验证Spring测试配置

### 调试步骤
1. 检查测试日志输出
2. 验证Mock配置
3. 确认测试数据正确性
4. 检查Spring上下文加载

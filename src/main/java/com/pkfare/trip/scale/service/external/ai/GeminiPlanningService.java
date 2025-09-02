package com.pkfare.trip.scale.service.external.ai;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.gson.Gson;
import com.pkfare.trip.scale.agent.planning.PlanningPrompt;
import com.pkfare.trip.scale.config.GoogleConfig;
import com.pkfare.trip.scale.exception.ExternalApiException;
import com.pkfare.trip.scale.function.UserEventFilter;
import com.pkfare.trip.scale.model.dto.SubmitAiPlanInfo;
import io.reactivex.rxjava3.core.Flowable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Gemini旅行计划生成服务
 *
 * @author Trip Scale Team
 */
@Slf4j
@Service
public class GeminiPlanningService {

  private final BaseAgent planningAgent;
  private final InMemoryRunner runner;
  private final Gson gson;

  public GeminiPlanningService() {
    this.planningAgent = createPlanningAgent();
    this.runner = new InMemoryRunner(planningAgent);
    this.gson = new Gson();
  }

  /**
   * 生成AI计划
   *
   * @param planInfo 计划信息
   * @return AI生成的计划文本
   */
  public String generateAiPlan(SubmitAiPlanInfo planInfo) {
    log.info("Generating AI plan for trip to: {}",
        planInfo.getGeneratePlanParam().getTrip_routes().get(0).getDestination_city());

    try {
      String prompt = buildPrompt(planInfo);
      String sessionId = UUID.randomUUID().toString();
      String userId = "trip-planner";

      ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
      state.put("sessionId", sessionId);
      state.put("userId", userId);
      Session session = runner.sessionService().createSession(planningAgent.name(), userId,state, sessionId).blockingGet();

      Content userMsg = Content.fromParts(Part.fromText(prompt));

      Flowable<Event> events = runner.runAsync(userId, sessionId, userMsg);
      StringBuilder result = new StringBuilder();

      events.filter(UserEventFilter.instance()).blockingForEach(event -> {
        log.debug("Received event: {}", event);
        if (event.content().isPresent()) {
          Content content = event.content().get();
          result.append(content.text());
        }
      });

      String aiPlan = result.toString();
      log.info("AI plan generated successfully, length: {} characters", aiPlan.length());

      return parseAiResponse(aiPlan);

    } catch (Exception e) {
      log.error("Failed to generate AI plan", e);
      throw new ExternalApiException("GEMINI_PLANNING_ERROR",
          "Failed to generate AI plan: " + e.getMessage(), 500, "GeminiPlanningService", e);
    }
  }

  /**
   * 构建提示词
   *
   * @param planInfo 计划信息
   * @return 提示词
   */
  private String buildPrompt(SubmitAiPlanInfo planInfo) {
    try {
      String planInfoJson = gson.toJson(planInfo);
      return PlanningPrompt.buildPrompt(planInfoJson);
    } catch (Exception e) {
      log.error("Failed to build prompt", e);
      throw new RuntimeException("Failed to build prompt", e);
    }
  }

  /**
   * 解析AI响应
   *
   * @param response AI响应
   * @return 解析后的计划文本
   */
  private String parseAiResponse(String response) {
    if (response == null || response.trim().isEmpty()) {
      return "AI生成的旅行计划暂时不可用，请稍后重试。";
    }

    // 简单的响应清理，去除多余的空行和格式化
    return response.trim()
        .replaceAll("\\n{3,}", "\n\n")  // 将3个或更多连续换行替换为2个
        .replaceAll("\\s+$", "");       // 去除末尾空白字符
  }

  /**
   * 创建计划生成Agent
   *
   * @return BaseAgent
   */
  private BaseAgent createPlanningAgent() {
    return LlmAgent.builder()
        .name("trip_planning_agent")
        .model(GoogleConfig.GEMINI_2_5_PRO)
        .description("Agent to generate detailed trip plans based on flight, hotel, and activity information.")
        .instruction(PlanningPrompt.PLANNING_PROMPT)
        .build();
  }
}

package com.pkfare.trip.scale.config;


import jakarta.annotation.PostConstruct;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import com.pkfare.trip.scale.agent.planning.PlanningAgent;

@Configuration
public class SpringAwareConfig  implements ApplicationContextAware {
  private ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  @PostConstruct
  public void init() {
    PlanningAgent.setApplicationContext(applicationContext);
  }
}

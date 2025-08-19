package com.pkfare.trip.scale.config;

import jakarta.annotation.PostConstruct;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogConfig {

  @PostConstruct
  public void init(){
    System.setProperty("jdk.httpclient.HttpClient.log", "all");
    Logger root = Logger.getLogger("");
    ConsoleHandler ch = new ConsoleHandler();
    ch.setLevel(Level.ALL);
    root.addHandler(ch);
    Logger.getLogger("jdk.httpclient").setLevel(Level.ALL);
    Logger.getLogger("java.net.http").setLevel(Level.ALL);
  }

}

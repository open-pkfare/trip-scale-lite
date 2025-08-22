package com.pkfare.trip.scale;

import com.google.adk.web.AdkWebServer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.plaf.basic.BasicInternalFrameTitlePane.SystemMenuBar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
public class TripScaleApplication {
    public static void main(String[] args) {
        String serverType = System.getProperty("serverType","server");
        switch (serverType){
            case "dev":
                System.setProperty("org.apache.tomcat.websocket.DEFAULT_BUFFER_SIZE", String.valueOf(10485760));
                SpringApplication.run(AdkWebServer.class, args);
                break;
            default:
                SpringApplication.run(TripScaleApplication.class, args);
                break;
        }
    }
}
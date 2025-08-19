package com.pkfare.trip.scale;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TripScaleApplication {
    public static void main(String[] args) {
        System.setProperty("jdk.httpclient.HttpClient.log", "all");
        Logger root = Logger.getLogger("");
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.ALL);
        root.addHandler(ch);
        Logger.getLogger("jdk.httpclient").setLevel(Level.ALL);
        Logger.getLogger("java.net.http").setLevel(Level.ALL);
        SpringApplication.run(TripScaleApplication.class, args);
    }
}
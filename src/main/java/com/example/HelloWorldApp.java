package com.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HelloWorldApp {
    public static void main(String[] args) {
        // Read configuration from environment variable
        String rateEnv = System.getenv("INTERVAL_SECONDS");
        long intervalSeconds = 10;
        if (rateEnv != null && !rateEnv.isEmpty()) {
            try {
                intervalSeconds = Long.parseLong(rateEnv);
            } catch (NumberFormatException e) {
                System.err.println("Invalid INTERVAL_SECONDS environment variable value: '" + rateEnv + "'. Falling back to default of 10 seconds.");
            }
        }

        System.out.println("Starting Java Hello World Service...");
        System.out.println("Output interval: " + intervalSeconds + " seconds.");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        while (true) {
            System.out.println("Hello World - " + LocalDateTime.now().format(formatter));
            try {
                Thread.sleep(intervalSeconds * 1000);
            } catch (InterruptedException e) {
                System.out.println("Service interrupted. Exiting.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}

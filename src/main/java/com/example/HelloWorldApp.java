package com.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HelloWorldApp {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        String rateEnv = System.getenv("INTERVAL_SECONDS");
        long intervalSeconds = parseInterval(rateEnv);

        System.out.println("Starting Java Hello World Service...");
        System.out.println("Output interval: " + intervalSeconds + " seconds.");

        while (true) {
            System.out.println(formatHelloMessage(LocalDateTime.now()));
            try {
                Thread.sleep(intervalSeconds * 1000);
            } catch (InterruptedException e) {
                System.out.println("Service interrupted. Exiting.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public static long parseInterval(String rateEnv) {
        long defaultInterval = 10;
        if (rateEnv != null && !rateEnv.isEmpty()) {
            try {
                return Long.parseLong(rateEnv);
            } catch (NumberFormatException e) {
                System.err.println("Invalid INTERVAL_SECONDS environment variable value: '" + rateEnv + "'. Falling back to default of 10 seconds.");
            }
        }
        return defaultInterval;
    }

    public static String formatHelloMessage(LocalDateTime dateTime) {
        return "Hello World - " + dateTime.format(FORMATTER);
    }
}


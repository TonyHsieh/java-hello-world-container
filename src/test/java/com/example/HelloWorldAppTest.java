package com.example;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HelloWorldAppTest {

    @Test
    public void testParseInterval_valid() {
        assertEquals(5, HelloWorldApp.parseInterval("5"));
        assertEquals(60, HelloWorldApp.parseInterval("60"));
    }

    @Test
    public void testParseInterval_invalid() {
        assertEquals(10, HelloWorldApp.parseInterval("invalid"));
        assertEquals(10, HelloWorldApp.parseInterval("5.5"));
    }

    @Test
    public void testParseInterval_null() {
        assertEquals(10, HelloWorldApp.parseInterval(null));
    }

    @Test
    public void testParseInterval_empty() {
        assertEquals(10, HelloWorldApp.parseInterval(""));
    }

    @Test
    public void testFormatHelloMessage() {
        LocalDateTime testTime = LocalDateTime.of(2026, 7, 4, 12, 30, 45);
        String expectedMessage = "Hello World - 2026-07-04 12:30:45";
        assertEquals(expectedMessage, HelloWorldApp.formatHelloMessage(testTime));
    }

    @Test
    public void testVersion() {
        assertEquals("v0.0.2", HelloWorldApp.VERSION);
    }
}

package com.chatdiet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Pulls in {@code actuator.properties}, the committed Actuator settings. They can't go in
 * {@code application.yml}, which is gitignored and supplied per machine.
 */
@Configuration
@PropertySource("classpath:actuator.properties")
public class ActuatorConfig {
}

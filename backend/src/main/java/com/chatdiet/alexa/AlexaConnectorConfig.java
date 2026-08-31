package com.chatdiet.alexa;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Adds a second, plaintext HTTP connector bound to {@code 127.0.0.1} only, alongside the app's
 * main HTTPS connector on {@code server.port} (8443, tailnet-facing). This second connector is
 * where Tailscale Funnel's public {@code https://<tailnet>/alexa} traffic lands after being
 * reverse-proxied to {@code localhost}, per {@code scripts/TAILSCALE-ALEXA-CONFIG.md} - Funnel
 * terminates TLS itself, so this connector never needs a certificate, and binding to loopback
 * only (never {@code 0.0.0.0}) means nothing on this port is reachable except through that proxy
 * or from the box itself. {@link AlexaPathIsolationFilter} restricts what it'll actually serve.
 */
@Component
public class AlexaConnectorConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final int alexaPort;

    public AlexaConnectorConfig(@Value("${chat-diet.alexa.port:8081}") int alexaPort) {
        this.alexaPort = alexaPort;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        var connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(alexaPort);
        connector.setScheme("http");
        connector.setSecure(false);
        connector.setProperty("address", "127.0.0.1");
        factory.addAdditionalConnectors(connector);
    }
}

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
 *
 * <p>In Docker the container's own loopback isn't reachable through a published port, so
 * docker-compose.yml sets {@code chat-diet.alexa.address} to {@code 0.0.0.0} and publishes the
 * port on the host's {@code 127.0.0.1} only, which keeps the same loopback-only exposure.
 */
@Component
public class AlexaConnectorConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final int alexaPort;
    private final String alexaAddress;

    public AlexaConnectorConfig(@Value("${chat-diet.alexa.port:8081}") int alexaPort,
                                @Value("${chat-diet.alexa.address:127.0.0.1}") String alexaAddress) {
        this.alexaPort = alexaPort;
        this.alexaAddress = alexaAddress;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        var connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(alexaPort);
        connector.setScheme("http");
        connector.setSecure(false);
        connector.setProperty("address", alexaAddress);
        factory.addAdditionalConnectors(connector);
    }
}

package com.chatdiet.alexa;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Defense in depth alongside Tailscale Funnel's own {@code --set-path=/alexa} restriction
 * (see {@code scripts/TAILSCALE-ALEXA-CONFIG.md}): the {@code /alexa} path is servable only on
 * the loopback-only Alexa connector ({@link AlexaConnectorConfig}), and nothing under
 * {@code /alexa} is servable on the tailnet-facing HTTPS connector. Even if Funnel's path
 * restriction were ever misconfigured, this still confines the public surface to exactly one
 * path on exactly one connector.
 *
 * <p>Runs before {@link AlexaSignatureFilter} ({@code @Order} 1 vs. 2), so a request on the wrong
 * connector/path is already rejected before signature verification would fail for the less
 * specific reason of missing headers on traffic that was never going to be Alexa's.
 */
@Component
@Order(1)
public class AlexaPathIsolationFilter extends OncePerRequestFilter {

    private static final String ALEXA_PATH = "/alexa";

    private final int alexaPort;

    public AlexaPathIsolationFilter(@Value("${chat-diet.alexa.port:8081}") int alexaPort) {
        this.alexaPort = alexaPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var onAlexaConnector = request.getLocalPort() == alexaPort;
        var isAlexaPath = request.getRequestURI().startsWith(ALEXA_PATH);

        if (onAlexaConnector != isAlexaPath) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}

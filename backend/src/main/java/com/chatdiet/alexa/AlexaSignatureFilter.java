package com.chatdiet.alexa;

import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.servlet.verifiers.AlexaHttpRequest;
import com.amazon.ask.servlet.verifiers.SkillRequestSignatureVerifier;
import com.amazon.ask.servlet.verifiers.SkillRequestTimestampVerifier;
import com.amazon.ask.util.JacksonSerializer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Verifies every request to {@code /alexa} is genuinely from Alexa before anything else touches
 * it - the {@code Signature}/{@code SignatureCertChainUrl} headers against the raw request body
 * ({@link SkillRequestSignatureVerifier}, which validates the cert chain against Amazon's root CA
 * and caches the downloaded certificate itself) and the request timestamp against a tolerance
 * ({@link SkillRequestTimestampVerifier}, replay-attack protection). Any failure is a 400, before
 * the request reaches {@link AlexaController} or anything backed by real data.
 *
 * <p>Reads the body once via {@link CachedBodyHttpServletRequestWrapper} and passes the cached
 * wrapper downstream, since the signature is computed over the raw bytes but the controller still
 * needs to parse the same bytes as JSON afterward.
 *
 * <p>Runs after {@link AlexaPathIsolationFilter} ({@code @Order} 2 vs. 1) so a request on the
 * wrong connector/path is already rejected with the cleaner "not found" outcome before signature
 * verification - which would fail anyway, but for the less specific reason of missing headers on
 * traffic that was never going to be Alexa's in the first place.
 */
@Component
@Order(2)
public class AlexaSignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AlexaSignatureFilter.class);

    private static final String ALEXA_PATH = "/alexa";
    private static final String SIGNATURE_HEADER = "Signature";
    private static final String CERT_CHAIN_URL_HEADER = "SignatureCertChainUrl";

    /** Alexa's own tolerance for how stale a request's timestamp may be, per the ASK SDK's default. */
    private static final long TIMESTAMP_TOLERANCE = 150;

    private final SkillRequestSignatureVerifier signatureVerifier = new SkillRequestSignatureVerifier();
    private final SkillRequestTimestampVerifier timestampVerifier =
            new SkillRequestTimestampVerifier(TIMESTAMP_TOLERANCE, TimeUnit.SECONDS);
    private final JacksonSerializer serializer = new JacksonSerializer();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ALEXA_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var cachedRequest = new CachedBodyHttpServletRequestWrapper(request);
        var signature = cachedRequest.getHeader(SIGNATURE_HEADER);
        var certChainUrl = cachedRequest.getHeader(CERT_CHAIN_URL_HEADER);

        if (signature == null || certChainUrl == null) {
            log.warn("Alexa request missing signature headers");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try {
            var alexaRequest = new CachedAlexaHttpRequest(cachedRequest.getCachedBody(), signature, certChainUrl);
            signatureVerifier.verify(alexaRequest);
            timestampVerifier.verify(alexaRequest);
        } catch (RuntimeException e) {
            log.warn("Alexa request failed signature/timestamp verification: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        chain.doFilter(cachedRequest, response);
    }

    /** Adapts a cached request body/headers to what the SDK's verifiers need, with no servlet-api coupling. */
    private final class CachedAlexaHttpRequest implements AlexaHttpRequest {

        private final byte[] body;
        private final String signature;
        private final String certChainUrl;

        CachedAlexaHttpRequest(byte[] body, String signature, String certChainUrl) {
            this.body = body;
            this.signature = signature;
            this.certChainUrl = certChainUrl;
        }

        @Override
        public String getBaseEncoded64Signature() {
            return signature;
        }

        @Override
        public String getSigningCertificateChainUrl() {
            return certChainUrl;
        }

        @Override
        public byte[] getSerializedRequestEnvelope() {
            return body;
        }

        @Override
        public RequestEnvelope getDeserializedRequestEnvelope() {
            return serializer.deserialize(new String(body, StandardCharsets.UTF_8), RequestEnvelope.class);
        }
    }
}

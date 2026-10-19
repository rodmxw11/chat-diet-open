package com.chatdiet.actuator;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A browsable index of Actuator's endpoints at {@code /actuator/html}, linked from the About page.
 * The page itself is static; it fetches {@code /actuator} and renders the {@code _links} map, so it
 * always matches whatever endpoints are exposed. Lives under the Actuator base path but is a plain
 * MVC handler - Actuator's own mapping only claims its registered endpoint paths.
 */
@RestController
public class ActuatorHtmlController {

    private final String page;

    public ActuatorHtmlController() throws IOException {
        this.page = new ClassPathResource("actuator/index.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @GetMapping(value = "/actuator/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page);
    }
}

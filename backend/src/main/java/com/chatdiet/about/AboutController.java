package com.chatdiet.about;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoint backing the About page. */
@RestController
public class AboutController {

    private final AboutService aboutService;

    public AboutController(AboutService aboutService) {
        this.aboutService = aboutService;
    }

    @GetMapping("/api/about")
    public AboutInfo about() {
        return aboutService.current();
    }
}

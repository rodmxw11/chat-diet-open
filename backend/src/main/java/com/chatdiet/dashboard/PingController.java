package com.chatdiet.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trivial connectivity heartbeat for the frontend's offline/online detection. No DB touch. */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public String ping() {
        return "ok";
    }
}

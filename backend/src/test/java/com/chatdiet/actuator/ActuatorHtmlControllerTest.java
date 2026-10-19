package com.chatdiet.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActuatorHtmlControllerTest {

    @Test
    void servesTheIndexPageAsHtml() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new ActuatorHtmlController()).build();

        mockMvc.perform(get("/actuator/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("fetch('/actuator'")));
    }
}

package com.chatdiet.alexa;

import com.chatdiet.chat.ChatService;
import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FastFoodLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the controller with a mocked {@link ChatService} and fast path, so no AI context boots and
 * no model calls are made. Signature verification is {@link AlexaSignatureFilter}'s job and isn't
 * in play here - these pin request routing, session handling, and what gets sent to the chat turn.
 */
class AlexaControllerTest {

    private MockMvc mockMvc;
    private ChatService chatService;
    private FastFoodLogService fastFoodLogService;

    @BeforeEach
    void standaloneController() {
        chatService = mock(ChatService.class);
        fastFoodLogService = mock(FastFoodLogService.class);
        when(fastFoodLogService.tryHandle(any(), any(), anyString())).thenReturn(Optional.empty());
        when(chatService.reply(any(), any(), anyString(), anyBoolean())).thenReturn("Logged a banana, 105 calories.");

        var dayBoundaryService = new DayBoundaryService();
        ReflectionTestUtils.setField(dayBoundaryService, "dayRolloverHour", 4);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AlexaController(fastFoodLogService, chatService, dayBoundaryService))
                .build();
    }

    @Test
    void launchSaysReadyAndKeepsListening() throws Exception {
        send(launchRequest())
                .andExpect(jsonPath("$.response.outputSpeech.ssml").value("<speak>Ready.</speak>"))
                .andExpect(jsonPath("$.response.shouldEndSession").value(false))
                .andExpect(jsonPath("$.response.reprompt.outputSpeech.ssml").exists());
        verifyNoInteractions(chatService, fastFoodLogService);
    }

    @Test
    void ateIntentSendsTheCarrierPhraseOnTheVoiceChannel() throws Exception {
        send(intentRequest("AteIntent", "a banana"))
                .andExpect(jsonPath("$.response.shouldEndSession").value(false))
                .andExpect(jsonPath("$.response.outputSpeech.ssml").value(
                        "<speak>Logged a banana, <say-as interpret-as=\"cardinal\">105</say-as> calories.</speak>"));

        verify(chatService).reply(any(LocalDate.class), any(LocalDateTime.class), eq("I ate a banana"), eq(true));
    }

    @Test
    void noteAndWeightIntentsUseTheirOwnCarrierPhrases() throws Exception {
        send(intentRequest("NoteIntent", "i skipped lunch"));
        send(intentRequest("WeightIntent", "213.4"));

        verify(chatService).reply(any(), any(), eq("Note that i skipped lunch"), eq(true));
        verify(chatService).reply(any(), any(), eq("Weight 213.4"), eq(true));
    }

    @Test
    void aFastPathHitNeverCallsTheModel() throws Exception {
        when(fastFoodLogService.tryHandle(any(), any(), eq("I ate 142 grams of cheerios")))
                .thenReturn(Optional.of("Logged 142g cheerios."));

        send(intentRequest("AteIntent", "142 grams of cheerios"))
                .andExpect(jsonPath("$.response.shouldEndSession").value(false));

        verify(chatService, never()).reply(any(), any(), anyString(), anyBoolean());
    }

    @Test
    void theTurnIsFiledUnderAlexasRequestTime() throws Exception {
        // 3:30am local is before the 4am rollover, so it belongs to the previous metabolic day.
        var timestamp = LocalDateTime.of(2026, 9, 27, 3, 30)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toString();

        send(intentRequest("AteIntent", "a banana", timestamp));

        verify(chatService).reply(eq(LocalDate.of(2026, 9, 26)), eq(LocalDateTime.of(2026, 9, 27, 3, 30)),
                eq("I ate a banana"), eq(true));
    }

    @Test
    void aBareAnswerGoesToTheModelWithNoCarrierPhrase() throws Exception {
        send(choiceRequest("\"choice\": {\"name\": \"choice\", \"value\": \"medium\"}"));
        send(choiceRequest("\"ordinal\": {\"name\": \"ordinal\", \"value\": \"2\"}"));
        send(choiceRequest("\"number\": {\"name\": \"number\", \"value\": \"150\"}, "
                + "\"unit\": {\"name\": \"unit\", \"value\": \"grams\"}"));
        send(intentRequest("AMAZON.YesIntent", null));

        verify(chatService).reply(any(), any(), eq("medium"), eq(true));
        verify(chatService).reply(any(), any(), eq("2"), eq(true));
        verify(chatService).reply(any(), any(), eq("150 grams"), eq(true));
        verify(chatService).reply(any(), any(), eq("yes"), eq(true));
    }

    @Test
    void aChoiceWithNoRecognizedValueWritesNothing() throws Exception {
        send(choiceRequest("\"choice\": {\"name\": \"choice\", \"value\": \"?\"}"))
                .andExpect(jsonPath("$.response.outputSpeech.ssml").value("<speak>Sorry?</speak>"));
        verifyNoInteractions(chatService, fastFoodLogService);
    }

    @Test
    void aQuestionGetsAnAnswerReprompt() throws Exception {
        when(chatService.reply(any(), any(), anyString(), anyBoolean()))
                .thenReturn("One apple - small, medium, or large?");

        send(intentRequest("AteIntent", "an apple"))
                .andExpect(jsonPath("$.response.shouldEndSession").value(false))
                .andExpect(jsonPath("$.response.reprompt.outputSpeech.ssml")
                        .value("<speak>Say your answer, or stop.</speak>"));
    }

    @Test
    void fallbackWritesNothing() throws Exception {
        send(intentRequest("AMAZON.FallbackIntent", null))
                .andExpect(jsonPath("$.response.outputSpeech.ssml").value("<speak>Sorry?</speak>"))
                .andExpect(jsonPath("$.response.shouldEndSession").value(false));
        verifyNoInteractions(chatService, fastFoodLogService);
    }

    @Test
    void anEmptySlotWritesNothing() throws Exception {
        send(intentRequest("AteIntent", null))
                .andExpect(jsonPath("$.response.outputSpeech.ssml").value("<speak>Sorry?</speak>"));
        verifyNoInteractions(chatService, fastFoodLogService);
    }

    @Test
    void stopEndsTheSession() throws Exception {
        send(intentRequest("AMAZON.StopIntent", null))
                .andExpect(jsonPath("$.response.shouldEndSession").value(true))
                .andExpect(jsonPath("$.response.reprompt").doesNotExist());
    }

    @Test
    void aFailedTurnClosesTheSession() throws Exception {
        when(chatService.reply(any(), any(), anyString(), anyBoolean())).thenThrow(new IllegalStateException("boom"));

        send(intentRequest("AteIntent", "a banana"))
                .andExpect(jsonPath("$.response.shouldEndSession").value(true));
    }

    @Test
    void ssmlDropsMarkersAndMarkdownEscapesXmlAndLeavesDecimalsAlone() {
        assertThat(AlexaController.toSsmlBody("**1,500** kcal & 182.4 lb [ref:item:12] <ok>"))
                .isEqualTo("<say-as interpret-as=\"cardinal\">1500</say-as> kcal &amp; 182.4 lb &lt;ok");
    }

    private ResultActions send(String body) throws Exception {
        return mockMvc.perform(post("/alexa").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private static String launchRequest() {
        return envelope("""
                {"type": "LaunchRequest", "requestId": "r1", "timestamp": "2026-09-27T15:00:00Z", "locale": "en-US"}""");
    }

    private static String intentRequest(String intentName, String text) {
        return intentRequest(intentName, text, java.time.Instant.now().toString());
    }

    private static String choiceRequest(String slotsJson) {
        return envelope("""
                {"type": "IntentRequest", "requestId": "r1", "timestamp": "%s", "locale": "en-US",
                 "intent": {"name": "ChoiceIntent", "confirmationStatus": "NONE", "slots": {%s}}}"""
                .formatted(java.time.Instant.now().toString(), slotsJson));
    }

    private static String intentRequest(String intentName, String text, String timestamp) {
        var slots = text == null ? "{}" : """
                {"text": {"name": "text", "value": "%s", "confirmationStatus": "NONE"}}""".formatted(text);
        return envelope("""
                {"type": "IntentRequest", "requestId": "r1", "timestamp": "%s", "locale": "en-US",
                 "intent": {"name": "%s", "confirmationStatus": "NONE", "slots": %s}}"""
                .formatted(timestamp, intentName, slots));
    }

    private static String envelope(String request) {
        return """
                {"version": "1.0",
                 "session": {"new": true, "sessionId": "s1", "application": {"applicationId": "amzn1.ask.skill.test"},
                             "user": {"userId": "u1"}},
                 "context": {"System": {"application": {"applicationId": "amzn1.ask.skill.test"},
                             "user": {"userId": "u1"}, "device": {"deviceId": "d1", "supportedInterfaces": {}}}},
                 "request": %s}""".formatted(request);
    }
}

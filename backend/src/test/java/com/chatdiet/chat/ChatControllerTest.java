package com.chatdiet.chat;

import com.chatdiet.chart.ChartResultContext;
import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FastFoodLogService;
import com.chatdiet.sql.SqlResultContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the controller with a mocked {@link ChatService} so no AI context boots and no model
 * calls are made - these pin request handling and date resolution, not model behavior.
 */
class ChatControllerTest {

    private MockMvc mockMvc;
    private ChatService chatService;
    private FastFoodLogService fastFoodLogService;
    private ConversationHistoryStore historyStore;
    private DayBoundaryService dayBoundaryService;

    @BeforeEach
    void standaloneController() {
        chatService = mock(ChatService.class);
        fastFoodLogService = mock(FastFoodLogService.class);
        historyStore = mock(ConversationHistoryStore.class);

        dayBoundaryService = new DayBoundaryService();
        ReflectionTestUtils.setField(dayBoundaryService, "dayRolloverHour", 4);

        var chartResultContext = mock(ChartResultContext.class);
        var sqlResultContext = mock(SqlResultContext.class);
        when(chartResultContext.series()).thenReturn(Optional.empty());
        when(sqlResultContext.answer()).thenReturn(Optional.empty());
        when(chatService.reply(any(), any(), anyString())).thenReturn("ok");
        when(fastFoodLogService.tryHandle(any(), any(), anyString())).thenReturn(Optional.empty());

        var chatCostCalculator = mock(ChatCostCalculator.class);
        when(chatCostCalculator.costFor(any())).thenReturn(new ChatCostCalculator.DailyCost(0.0, 0.0));

        var controller = new ChatController(chatService, fastFoodLogService, historyStore, dayBoundaryService,
                chartResultContext, sqlResultContext, chatCostCalculator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void aNormalMessageIsFiledUnderToday() throws Exception {
        postChat("{\"text\":\"I ate a banana\"}");

        assertThat(capturedDate()).isEqualTo(dayBoundaryService.today());
    }

    /**
     * The offline-queue case that motivated tracking composition time: a message written just
     * before midnight and replayed after the 4am rollover belongs to the day it was written on.
     */
    @Test
    void anOfflineMessageIsFiledUnderTheDayItWasComposed() throws Exception {
        var composedLastNight = LocalDateTime.now().minusDays(1).withHour(23).withMinute(50);
        postChat("{\"text\":\"late snack\",\"clientSentAt\":\"" + isoUtc(composedLastNight) + "\"}");

        assertThat(capturedDate())
                .isEqualTo(dayBoundaryService.metabolicDateOf(composedLastNight))
                .isNotEqualTo(dayBoundaryService.today());
    }

    @Test
    void aFutureTimestampFromASkewedClockIsClampedToToday() throws Exception {
        var twoDaysOut = LocalDateTime.now().plusDays(2);
        postChat("{\"text\":\"hello\",\"clientSentAt\":\"" + isoUtc(twoDaysOut) + "\"}");

        assertThat(capturedDate()).isEqualTo(dayBoundaryService.today());
    }

    @Test
    void aMalformedTimestampFallsBackToTodayWithoutFailing() throws Exception {
        postChat("{\"text\":\"hello\",\"clientSentAt\":\"not-a-timestamp\"}");

        assertThat(capturedDate()).isEqualTo(dayBoundaryService.today());
    }

    /** Queued requests outlive a deploy, so a body still carrying the removed field must not 400. */
    @Test
    void aQueuedRequestStillCarryingTheOldSessionIdIsAccepted() throws Exception {
        postChat("{\"text\":\"hello\",\"sessionId\":\"abc-123\"}");

        assertThat(capturedDate()).isEqualTo(dayBoundaryService.today());
    }

    @Test
    void aFastPathHitRepliesWithoutInvokingTheModel() throws Exception {
        when(fastFoodLogService.tryHandle(any(), any(), anyString()))
                .thenReturn(Optional.of("Logged \"cheerios\" (142g): 507 kcal."));

        mockMvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"text\":\"142g cheerios\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Logged \"cheerios\" (142g): 507 kcal."));

        verify(chatService, never()).reply(any(), any(), anyString());
    }

    @Test
    void historyReturnsTodaysConversationOldestFirst() throws Exception {
        var today = dayBoundaryService.today();
        when(historyStore.messagesFor(today)).thenReturn(List.of(
                new ChatMessage(today, "user", "morning", today.atTime(8, 0)),
                new ChatMessage(today, "assistant", "logged", today.atTime(8, 1))));

        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metabolicDate").value(today.toString()))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].text").value("morning"))
                .andExpect(jsonPath("$.messages[1].text").value("logged"));
    }

    @Test
    void historyForAnExplicitDateReturnsThatDaysConversation() throws Exception {
        var pastDay = dayBoundaryService.today().minusDays(3);
        when(historyStore.messagesFor(pastDay)).thenReturn(List.of(
                new ChatMessage(pastDay, "user", "old question", pastDay.atTime(9, 0)),
                new ChatMessage(pastDay, "assistant", "old answer", pastDay.atTime(9, 1))));

        mockMvc.perform(get("/api/chat/history").param("date", pastDay.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metabolicDate").value(pastDay.toString()))
                .andExpect(jsonPath("$.messages[0].text").value("old question"))
                .andExpect(jsonPath("$.messages[1].text").value("old answer"));
    }

    private void postChat(String json) throws Exception {
        mockMvc.perform(post("/api/chat").contentType("application/json").content(json))
                .andExpect(status().isOk());
    }

    private LocalDate capturedDate() {
        var captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(chatService).reply(captor.capture(), any(), anyString());
        return captor.getValue();
    }

    private static String isoUtc(LocalDateTime local) {
        return local.atZone(ZoneId.systemDefault()).toInstant().toString();
    }
}

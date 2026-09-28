package com.chatdiet.chat;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FoodLogVerificationContext;
import com.chatdiet.intent.PromptAssembler;
import com.chatdiet.sql.SqlUsageContext;
import com.chatdiet.weight.WeightLogVerificationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins where {@link ChatService#SAVE_REMINDER} goes: onto the text sent to the model when there's
 * history (whose "Logged ..." replies otherwise look tool-less), but never into the persisted
 * conversation, which must keep exactly what the user said. The model itself is mocked - whether
 * the note actually stops fake confirmations was measured separately against the real model.
 */
class ChatServiceSaveReminderTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 28);
    private static final LocalDateTime AT = DAY.atTime(12, 0);

    private ChatClient.ChatClientRequestSpec requestSpec;
    private ConversationHistoryStore historyStore;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        var callSpec = mock(ChatClient.CallResponseSpec.class);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Logged: 1 banana, 105 cal.")))));
        var chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        var builder = mock(ChatClient.Builder.class, RETURNS_SELF);
        when(builder.clone()).thenReturn(builder); // clone() is outside RETURNS_SELF's reach
        when(builder.build()).thenReturn(chatClient);

        var promptAssembler = mock(PromptAssembler.class);
        when(promptAssembler.tools(anyBoolean())).thenReturn(List.of());
        when(promptAssembler.systemPrompt(anyBoolean())).thenReturn("system");

        // The model's "Logged" claim is backed by a real tool call, so no corrective retry runs.
        var foodLogged = mock(FoodLogVerificationContext.class);
        when(foodLogged.wasLogged()).thenReturn(true);
        var sqlUsage = mock(SqlUsageContext.class);
        when(sqlUsage.usage()).thenReturn(Optional.empty());

        historyStore = mock(ConversationHistoryStore.class);
        chatService = new ChatService(builder, promptAssembler, historyStore, mock(DayBoundaryService.class),
                sqlUsage, foodLogged, mock(WeightLogVerificationContext.class));
    }

    @Test
    void withHistoryTheModelGetsTheReminderButHistoryKeepsTheOriginalText() {
        List<Message> history = List.of(new UserMessage("Today I ate 100 g egg"),
                new AssistantMessage("Logged \"egg\" (100g): 143 kcal."));
        when(historyStore.get(DAY)).thenReturn(history);

        chatService.reply(DAY, AT, "Today I ate 1 banana");

        var sent = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(sent.capture());
        assertThat(sent.getValue()).isEqualTo("Today I ate 1 banana" + ChatService.SAVE_REMINDER);
        verify(historyStore).append(eq(DAY), eq(AT), eq("Today I ate 1 banana"), anyString(), any(), any());
    }

    @Test
    void theFirstTurnOfTheDayIsSentUnchanged() {
        when(historyStore.get(DAY)).thenReturn(List.of());

        chatService.reply(DAY, AT, "Today I ate 1 banana");

        verify(requestSpec).user("Today I ate 1 banana");
    }
}

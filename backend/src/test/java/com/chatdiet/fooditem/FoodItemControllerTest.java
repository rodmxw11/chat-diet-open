package com.chatdiet.fooditem;

import com.chatdiet.fdc.FdcClient;
import com.chatdiet.openfoodfacts.OpenFoodFactsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the alias and portion-unit endpoints with mocked repositories - these pin the collision
 * contract (idempotent same-item re-add, 409 naming the owner for a foreign key) that the alias
 * UI depends on to stop silently faking success.
 */
class FoodItemControllerTest {

    private MockMvc mockMvc;
    private FoodItemRepository foodItemRepository;
    private FoodAliasRepository foodAliasRepository;
    private PortionUnitRepository portionUnitRepository;

    @BeforeEach
    void standaloneController() {
        foodItemRepository = mock(FoodItemRepository.class);
        foodAliasRepository = mock(FoodAliasRepository.class);
        portionUnitRepository = mock(PortionUnitRepository.class);
        var controller = new FoodItemController(foodItemRepository, foodAliasRepository,
                mock(PortionUnitService.class), portionUnitRepository,
                mock(FdcClient.class), mock(OpenFoodFactsClient.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void addingANewAliasSavesItAsUserSource() throws Exception {
        when(foodAliasRepository.findByAliasNormalized("greek yogurt")).thenReturn(Optional.empty());
        when(foodAliasRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/food-items/5/aliases").contentType("application/json")
                        .content("{\"alias\":\"Greek Yogurt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aliasNormalized").value("greek yogurt"))
                .andExpect(jsonPath("$.foodItemId").value(5))
                .andExpect(jsonPath("$.source").value("USER"));
    }

    @Test
    void reAddingAnAliasTheItemAlreadyOwnsIsIdempotent() throws Exception {
        when(foodAliasRepository.findByAliasNormalized("greek yogurt"))
                .thenReturn(Optional.of(new FoodAlias(7L, "greek yogurt", 5L, "USER", null)));

        mockMvc.perform(post("/api/food-items/5/aliases").contentType("application/json")
                        .content("{\"alias\":\"greek yogurt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        verify(foodAliasRepository, never()).save(any());
    }

    @Test
    void anAliasOwnedByADifferentItemReturns409NamingTheOwner() throws Exception {
        when(foodAliasRepository.findByAliasNormalized("greek yogurt"))
                .thenReturn(Optional.of(new FoodAlias(7L, "greek yogurt", 5L, "USER", null)));
        when(foodItemRepository.findById(5L)).thenReturn(Optional.of(new FoodItem(5L, "Chobani Greek Yogurt",
                null, 59.0, 10.0, 3.6, 0.4, 0.0, 3.2, 36.0, 0.1, 5.0, 141.0, null, "OFF", 3, null, null)));

        mockMvc.perform(post("/api/food-items/9/aliases").contentType("application/json")
                        .content("{\"alias\":\"greek yogurt\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.alias").value("greek yogurt"))
                .andExpect(jsonPath("$.owningItemId").value(5))
                .andExpect(jsonPath("$.owningItemName").value("Chobani Greek Yogurt"));

        verify(foodAliasRepository, never()).save(any());
    }

    @Test
    void portionUnitsCanBeListedAndDeleted() throws Exception {
        when(portionUnitRepository.findByFoodItemId(5L))
                .thenReturn(List.of(new PortionUnit(3L, 5L, "slice", 43.0, "MANUAL")));

        mockMvc.perform(get("/api/food-items/5/portion-units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unitName").value("slice"))
                .andExpect(jsonPath("$[0].grams").value(43.0));

        mockMvc.perform(delete("/api/food-items/portion-units/3"))
                .andExpect(status().isNoContent());
        verify(portionUnitRepository).deleteById(3L);
    }
}
package com.chatdiet.fooditem;

import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link FoodAlias}. */
public interface FoodAliasRepository extends ListCrudRepository<FoodAlias, Long> {

    /** The one lookup identity resolution is allowed to auto-select from: exact match, hit or miss. */
    Optional<FoodAlias> findByAliasNormalized(String aliasNormalized);

    /** Every alias pointing at a given food item, for the Food Items page's alias-list UI. */
    List<FoodAlias> findByFoodItemId(long foodItemId);
}

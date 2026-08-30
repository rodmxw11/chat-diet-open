package com.chatdiet.fooditem;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link PortionUnit} rows. */
public interface PortionUnitRepository extends ListCrudRepository<PortionUnit, Long> {

    List<PortionUnit> findByFoodItemId(Long foodItemId);

    @Query("SELECT * FROM portion_unit WHERE food_item_id = :foodItemId AND unit_name = :unitName")
    Optional<PortionUnit> findByFoodItemIdAndUnitName(Long foodItemId, String unitName);
}

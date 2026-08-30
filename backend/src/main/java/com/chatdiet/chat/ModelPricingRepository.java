package com.chatdiet.chat;

import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

/** Spring Data JDBC repository for {@link ModelPricing}. */
public interface ModelPricingRepository extends ListCrudRepository<ModelPricing, Long> {

    Optional<ModelPricing> findByModel(String model);
}

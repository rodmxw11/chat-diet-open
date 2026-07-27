package com.chatdiet.food;

public record LogFoodByUpcRequest(String upc, Double quantityServings, Double quantityG) {
}

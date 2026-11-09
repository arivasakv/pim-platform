package com.pim.backend.product.dto;

/**
 * One product in a search result (contract schema {@code ProductSummary}).
 *
 * @param business business name as sent to clients: {@code LIVERPOOL} or {@code SUBURBIA}
 */
public record ProductSummaryResponse(String sku, String ean, String title, String brand, String business) {}

package com.pim.backend.product;

/**
 * Business line a product belongs to. Declaration order is the display order of search results
 * (Liverpool first, then Suburbia). Marketplace products are never returned by the Mercaderías search
 * (spec FR-014).
 */
public enum Business {
    LIVERPOOL,
    SUBURBIA,
    MARKETPLACE
}

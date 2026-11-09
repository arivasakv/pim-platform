package com.pim.backend.product.repository;

import com.pim.backend.product.model.Ean;
import com.pim.backend.product.model.Product;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only product catalog held in memory, indexed by EAN (research R-03). It has no write methods, so a
 * search can never change it (spec FR-012).
 */
public final class InMemoryProductRepository implements ProductRepository {

    private final Map<String, List<Product>> productsByEan;

    /**
     * @throws IllegalStateException if the same EAN appears twice in one business (an EAN may repeat across
     *     businesses, but must be unique within one)
     */
    public InMemoryProductRepository(List<Product> products) {
        this.productsByEan = indexByEan(products);
    }

    @Override
    public List<Product> findByEan(Ean ean) {
        return productsByEan.getOrDefault(ean.value(), List.of());
    }

    private static Map<String, List<Product>> indexByEan(List<Product> products) {
        Map<String, List<Product>> index = new HashMap<>();
        Set<String> eanAndBusiness = new HashSet<>();
        for (Product product : products) {
            if (!eanAndBusiness.add(product.ean() + "|" + product.business())) {
                throw new IllegalStateException(
                        "EAN " + product.ean() + " appears more than once in business " + product.business());
            }
            index.computeIfAbsent(product.ean(), ean -> new ArrayList<>()).add(product);
        }
        Map<String, List<Product>> unmodifiable = new HashMap<>();
        index.forEach((ean, matches) -> unmodifiable.put(ean, List.copyOf(matches)));
        return Map.copyOf(unmodifiable);
    }
}

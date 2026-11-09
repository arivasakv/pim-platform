package com.pim.backend.product;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Finds existing products for a Mercaderías proposal. Kept free of HTTP types so the proposal flow and bulk
 * upload can reuse it later (spec TD-002).
 */
@Service
public class ProductSearchService {

    /** Mercaderías businesses; Marketplace products are never returned (spec FR-014). */
    private static final Set<Business> MERCADERIAS = EnumSet.of(Business.LIVERPOOL, Business.SUBURBIA);

    private static final Comparator<Product> RESULT_ORDER =
            Comparator.comparing(Product::business).thenComparing(Product::sku);

    private final ProductRepository productRepository;

    public ProductSearchService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Liverpool and Suburbia products whose EAN is exactly {@code ean} (spec FR-004, FR-014), Liverpool first,
     * then by SKU. Never changes the catalog (FR-012).
     */
    public List<Product> findExistingMercaderiasProducts(Ean ean) {
        return productRepository.findByEan(ean).stream()
                .filter(product -> MERCADERIAS.contains(product.business()))
                .sorted(RESULT_ORDER)
                .toList();
    }
}

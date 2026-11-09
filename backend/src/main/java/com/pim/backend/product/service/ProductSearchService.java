package com.pim.backend.product.service;

import com.pim.backend.product.model.Business;
import com.pim.backend.product.model.Ean;
import com.pim.backend.product.model.InvalidEanException;
import com.pim.backend.product.model.Product;
import com.pim.backend.product.repository.ProductRepository;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Finds existing products for a Mercaderías proposal. Validates the EAN and applies the search rule; kept
 * free of HTTP and DTO types so the proposal flow and bulk upload can reuse it later (spec TD-002;
 * constitution XII).
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
     * Liverpool and Suburbia products whose EAN is exactly {@code rawEan} once spaces and dashes are removed
     * (spec FR-003, FR-004, FR-014), Liverpool first, then by SKU. Never changes the catalog (FR-012).
     *
     * @throws InvalidEanException when {@code rawEan} is not a valid EAN (spec FR-010)
     */
    public List<Product> findExistingMercaderiasProducts(String rawEan) {
        Ean ean = Ean.parse(rawEan);
        return productRepository.findByEan(ean).stream()
                .filter(product -> MERCADERIAS.contains(product.business()))
                .sorted(RESULT_ORDER)
                .toList();
    }
}

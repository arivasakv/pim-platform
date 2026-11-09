package com.pim.backend.product.controller;

import com.pim.backend.product.dto.ProductCollectionResponse;
import com.pim.backend.product.dto.ProductSummaryResponse;
import com.pim.backend.product.model.Product;
import com.pim.backend.product.service.ProductSearchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/products?ean=}: finds existing Mercaderías products by exact EAN
 * (contracts/product-search.openapi.yaml). HTTP mapping and model-to-DTO mapping only; validation of the
 * EAN and the search rule live in {@link ProductSearchService} (constitution III, XII).
 *
 * <p>Bean Validation messages carry the error code; {@link ProductExceptionHandler} turns them into Problem
 * Details with the Spanish text.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductSearchService productSearchService;

    public ProductController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    @GetMapping
    public ProductCollectionResponse searchByEan(
            @RequestParam(name = "ean", required = false)
                    @NotBlank(message = "REQUIRED")
                    @Size(max = 20, message = "TOO_LONG")
                    @Pattern(regexp = "^[0-9 -]*$", message = "INVALID_CHARACTERS")
                    String ean) {
        List<ProductSummaryResponse> items = productSearchService.findExistingMercaderiasProducts(ean).stream()
                .map(ProductController::toSummary)
                .toList();
        return new ProductCollectionResponse(items, items.size());
    }

    private static ProductSummaryResponse toSummary(Product product) {
        return new ProductSummaryResponse(
                product.sku(),
                product.ean(),
                product.title(),
                product.brand(),
                product.business().name());
    }
}

package com.pim.backend.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/products?ean=}: finds existing Mercaderías products by exact EAN
 * (contracts/product-search.openapi.yaml). HTTP mapping only; the rule lives in {@link ProductSearchService}
 * (constitution III).
 *
 * <p>Bean Validation messages carry the error code; {@code ApiExceptionHandler} turns them into Problem
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
        Ean validEan = Ean.parse(ean);
        return ProductCollectionResponse.of(productSearchService.findExistingMercaderiasProducts(validEan).stream()
                .map(ProductSummaryResponse::from)
                .toList());
    }
}

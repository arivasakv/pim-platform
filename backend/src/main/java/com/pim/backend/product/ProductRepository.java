package com.pim.backend.product;

import java.util.List;

/**
 * Read access to the product catalog. In this phase the only implementation keeps sample data in memory
 * (constitution IX); callers depend on this interface so the storage can be replaced later.
 */
public interface ProductRepository {

    /** All products whose EAN is exactly {@code ean}, in any business; empty when there is none. */
    List<Product> findByEan(Ean ean);
}

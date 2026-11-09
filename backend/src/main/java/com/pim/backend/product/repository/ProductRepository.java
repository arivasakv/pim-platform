package com.pim.backend.product.repository;

import com.pim.backend.product.model.Ean;
import com.pim.backend.product.model.Product;
import java.util.List;

/**
 * Read access to the product catalog. The production implementation reads MongoDB
 * ({@link MongoProductRepository}, constitution IX); callers depend on this interface so the storage can
 * be replaced later.
 */
public interface ProductRepository {

    /** All products whose EAN is exactly {@code ean}, in any business; empty when there is none. */
    List<Product> findByEan(Ean ean);
}

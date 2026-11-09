package com.pim.backend.product.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Spring Data access to the {@code products} collection. Package-private; used by {@link MongoProductRepository}. */
interface ProductDocumentRepository extends MongoRepository<ProductDocument, String> {

    /** Documents whose {@code ean} equals the given value exactly, in any business. */
    List<ProductDocument> findByEan(String ean);
}

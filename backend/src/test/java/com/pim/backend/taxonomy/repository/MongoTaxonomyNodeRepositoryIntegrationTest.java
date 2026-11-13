package com.pim.backend.taxonomy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.taxonomy.model.NodeName;
import com.pim.backend.taxonomy.model.TaxonomyNode;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** Taxonomy nodes stored in MongoDB, collection {@code taxonomy_nodes} (plan decisions D-2, D-3; ADR 0003). */
@SpringBootTest
class MongoTaxonomyNodeRepositoryIntegrationTest {

    private static final Instant CREATED = Instant.parse("2026-11-12T10:15:30.123Z");
    private static final Instant UPDATED = Instant.parse("2026-11-12T11:00:00.456Z");

    private final MongoTemplate mongoTemplate;
    private final TaxonomyNodeRepository repository;

    MongoTaxonomyNodeRepositoryIntegrationTest(
            @Autowired MongoTemplate mongoTemplate, @Autowired TaxonomyNodeRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    @BeforeEach
    void emptyTheCollection() {
        mongoTemplate.remove(new Query(), "taxonomy_nodes");
    }

    private static TaxonomyNode node(String id, String name, String parentId) {
        return TaxonomyNode.create(id, NodeName.of(name), null, parentId, CREATED);
    }

    @Test
    void usesTheMongoImplementation() {
        assertThat(repository).isInstanceOf(MongoTaxonomyNodeRepository.class);
    }

    @Test
    void savesAndFindsANodeWithEveryFieldIgnoringCase() {
        TaxonomyNode node = TaxonomyNode.create("CLS000002", NodeName.of("Camisas"), "Formales", "CLS000001", CREATED)
                .withAttributes(List.of("ATR000002", "ATR000001"), UPDATED);

        repository.insert(node);

        assertThat(repository.findById("cls000002")).contains(node);
        assertThat(repository.findById("CLS999999")).isEmpty();
        Document stored = mongoTemplate.getCollection("taxonomy_nodes").find().first();
        assertThat(stored).isNotNull();
        assertThat(stored.getString("_id")).isEqualTo("CLS000002");
        assertThat(stored.getString("nameKey")).isEqualTo("camisas");
        assertThat(stored.getString("parentId")).isEqualTo("CLS000001");
        assertThat(stored.getList("attributeIds", String.class)).containsExactly("ATR000002", "ATR000001");
    }

    @Test
    void listsNodesByNameAndFindsThoseListingAnAttribute() {
        repository.insert(node("CLS000001", "Ropa", null).withAttributes(List.of("ATR000001"), UPDATED));
        repository.insert(node("CLS000002", "Calzado", null).withAttributes(List.of("ATR000001"), UPDATED));
        repository.insert(node("CLS000003", "Accesorios", null));

        assertThat(repository.findAll())
                .extracting(found -> found.name().value())
                .containsExactly("Accesorios", "Calzado", "Ropa");
        assertThat(repository.findAllListing("atr000001"))
                .extracting(TaxonomyNode::systemId)
                .containsExactly("CLS000002", "CLS000001");
    }

    @Test
    void namesAreUniqueAmongSiblingsOnly() {
        repository.insert(node("CLS000001", "Ropa", null));
        repository.insert(node("CLS000002", "Accesorios", "CLS000001"));
        repository.insert(node("CLS000003", "Calzado", null));

        assertThat(repository.existsWithName(null, NodeName.of(" ROPA "), null)).isTrue();
        assertThat(repository.existsWithName("cls000001", NodeName.of("accesorios"), null))
                .isTrue();
        assertThat(repository.existsWithName("CLS000001", NodeName.of("Accesorios"), "CLS000002"))
                .isFalse();
        assertThat(repository.existsWithName("CLS000003", NodeName.of("Accesorios"), null))
                .isFalse();

        repository.insert(node("CLS000004", "Accesorios", "CLS000003"));
        assertThatThrownBy(() -> repository.insert(node("CLS000005", "ACCESORIOS", "CLS000001")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.insert(node("CLS000006", "ropa", null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updatesAndDeletes() {
        TaxonomyNode node = node("CLS000001", "Ropa", null);
        repository.insert(node);

        TaxonomyNode renamed = node.renamed(NodeName.of("Ropa y accesorios"), null, UPDATED);
        repository.update(renamed);
        assertThat(repository.findById("CLS000001")).contains(renamed);

        repository.delete("cls000001");
        assertThat(repository.findAll()).isEmpty();
    }
}

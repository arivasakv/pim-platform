package com.pim.backend.product;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic sample catalog used in this phase instead of a database (constitution IX; data-model.md,
 * "Sample catalog"). It contains the fixed scenario products of spec.md plus generated products, 531 in
 * total. There is no randomness: every start builds the same catalog.
 */
public final class SampleProductCatalog {

    private static final int GENERATED_MERCADERIAS_PRODUCTS = 500;
    private static final int GENERATED_MARKETPLACE_PRODUCTS = 25;

    private static final String[] PRODUCT_WORDS = {
        "Sartén antiadherente",
        "Olla de acero",
        "Cafetera de goteo",
        "Tostador eléctrico",
        "Juego de toallas",
        "Lámpara de escritorio",
        "Almohada de plumas",
        "Mochila escolar",
        "Botella térmica",
        "Set de cuchillos",
        "Plancha de vapor",
        "Ventilador de pie",
        "Reloj de pared",
        "Cojín decorativo",
        "Exprimidor de cítricos",
        "Báscula de cocina",
        "Juego de sábanas",
        "Organizador de cajones",
        "Taza de cerámica",
        "Espejo de baño"
    };

    private static final String[] VARIANTS = {
        "24 cm", "28 cm", "1 L", "1.5 L", "2 piezas", "4 piezas", "blanco", "gris", "individual", "matrimonial"
    };

    private static final String[] BRANDS = {
        "CASA DEMO", "HOGAR DEMO", "COCINA DEMO", "CONFORT DEMO", "ESTILO DEMO", "BASICO DEMO"
    };

    private SampleProductCatalog() {}

    public static List<Product> create() {
        List<Product> products = new ArrayList<>(scenarioProducts());
        products.addAll(generatedMercaderiasProducts());
        products.addAll(generatedMarketplaceProducts());
        return List.copyOf(products);
    }

    /** Fixed products used by the spec's acceptance scenarios, the tests and the quickstart. */
    private static List<Product> scenarioProducts() {
        return List.of(
                new Product(
                        "1000001", "7501234567890", "Licuadora 10 velocidades 600 W", "MARCA DEMO", Business.LIVERPOOL),
                new Product("1000002", "7502000000017", "Batidora de mano 300 W", "HOGAR DEMO", Business.LIVERPOOL),
                new Product("1000003", "7502000000017", "Batidora de mano 300 W", "HOGAR DEMO", Business.SUBURBIA),
                new Product("1000004", "7501112223334", "Audífonos inalámbricos", "AUDIO DEMO", Business.MARKETPLACE),
                new Product("1000005", "12345670", "Termo de acero 500 ml", "CASA DEMO", Business.LIVERPOOL),
                new Product("1000006", "17501234567893", "Caja con 6 vasos de vidrio", "CASA DEMO", Business.SUBURBIA));
    }

    /** Liverpool (odd n) and Suburbia (even n) products: SKUs 1100001–1100500, EANs 7503 + 9 digits. */
    private static List<Product> generatedMercaderiasProducts() {
        List<Product> products = new ArrayList<>();
        for (int n = 1; n <= GENERATED_MERCADERIAS_PRODUCTS; n++) {
            Business business = n % 2 == 1 ? Business.LIVERPOOL : Business.SUBURBIA;
            products.add(generatedProduct(1_100_000 + n, "7503", n, business));
        }
        return products;
    }

    /** Marketplace products: SKUs 1200001–1200025, EANs 7504 + 9 digits. Never returned by the search. */
    private static List<Product> generatedMarketplaceProducts() {
        List<Product> products = new ArrayList<>();
        for (int n = 1; n <= GENERATED_MARKETPLACE_PRODUCTS; n++) {
            products.add(generatedProduct(1_200_000 + n, "7504", n, Business.MARKETPLACE));
        }
        return products;
    }

    private static Product generatedProduct(int sku, String eanPrefix, int n, Business business) {
        String title =
                PRODUCT_WORDS[n % PRODUCT_WORDS.length] + " " + VARIANTS[(n / PRODUCT_WORDS.length) % VARIANTS.length];
        String brand = BRANDS[n % BRANDS.length];
        return new Product(String.valueOf(sku), eanPrefix + String.format("%09d", n), title, brand, business);
    }
}

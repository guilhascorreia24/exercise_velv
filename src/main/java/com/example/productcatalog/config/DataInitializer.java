package com.example.productcatalog.config;

import com.example.productcatalog.product.Product;
import com.example.productcatalog.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds the in-memory database with dummy products on startup.
 * Names are the cartesian product ADJECTIVES x EDITIONS x ITEMS (20 x 3 x 25 = 1,500 unique names),
 * and prices use a fixed random seed, so the data set is identical on every run.
 * <p>
 * Runs as a {@link SmartInitializingSingleton} (after all beans exist, but before the embedded web server
 * starts) rather than an {@code ApplicationRunner}, which only runs once the HTTP port is already open
 * and would briefly serve an empty catalog.
 */
@Component
public class DataInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private record Item(String name, String category, double basePrice) {
    }

    private record Edition(String name, double priceFactor) {
    }

    private static final List<String> ADJECTIVES = List.of(
            "Classic", "Compact", "Deluxe", "Eco", "Elite", "Essential", "Premium", "Smart", "Ultra", "Vintage",
            "Modern", "Portable", "Rugged", "Sleek", "Urban", "Advanced", "Basic", "Signature", "Everyday", "Nordic");

    private static final List<Edition> EDITIONS = List.of(
            new Edition("Lite", 0.8), new Edition("Plus", 1.0), new Edition("Pro", 1.35));

    private static final List<Item> ITEMS = List.of(
            new Item("Headphones", "Electronics", 79.99),
            new Item("Headset", "Electronics", 59.99),
            new Item("Mouse", "Electronics", 29.99),
            new Item("Keyboard", "Electronics", 69.99),
            new Item("Monitor", "Electronics", 189.99),
            new Item("Webcam", "Electronics", 39.99),
            new Item("Speaker", "Electronics", 49.99),
            new Item("Smartwatch", "Electronics", 149.99),
            new Item("Office Chair", "Furniture", 199.99),
            new Item("Standing Desk", "Furniture", 349.99),
            new Item("Bookshelf", "Furniture", 119.99),
            new Item("Desk Lamp", "Home", 29.99),
            new Item("Coffee Mug", "Kitchen", 9.99),
            new Item("Blender", "Kitchen", 59.99),
            new Item("Water Bottle", "Outdoors", 14.99),
            new Item("Tent", "Outdoors", 159.99),
            new Item("Backpack", "Accessories", 45.99),
            new Item("Sunglasses", "Accessories", 65.99),
            new Item("Running Shoes", "Footwear", 85.99),
            new Item("Hiking Boots", "Footwear", 129.99),
            new Item("Notebook", "Stationery", 3.99),
            new Item("Ballpoint Pen", "Stationery", 1.49),
            new Item("Yoga Mat", "Sports", 24.99),
            new Item("Dumbbell Set", "Sports", 89.99),
            new Item("Tennis Racket", "Sports", 99.99));

    public static final int SEED_PRODUCT_COUNT = ADJECTIVES.size() * EDITIONS.size() * ITEMS.size();

    private final ProductRepository productRepository;

    public DataInitializer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (productRepository.count() > 0) {
            log.info("Products already present, skipping seed data");
            return;
        }
        productRepository.saveAll(generateProducts());
        log.info("Seeded {} products", SEED_PRODUCT_COUNT);
    }

    private static List<Product> generateProducts() {
        Random random = new Random(42);
        List<Product> products = new ArrayList<>(SEED_PRODUCT_COUNT);
        for (Edition edition : EDITIONS) {
            for (String adjective : ADJECTIVES) {
                for (Item item : ITEMS) {
                    String name = adjective + " " + item.name() + " " + edition.name();
                    double variation = 0.9 + random.nextDouble() * 0.2; // +/-10%
                    double price = roundToCents(item.basePrice() * edition.priceFactor() * variation);
                    products.add(new Product(name, item.category(), price));
                }
            }
        }
        return products;
    }

    private static double roundToCents(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}

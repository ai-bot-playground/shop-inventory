package com.shop.inventory.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.inventory.repo.OutboxRepository;
import com.shop.inventory.service.InventoryService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

public class InventorySteps {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();
    private final InventoryService service;
    private final OutboxRepository outbox;
    private final int port;

    public InventorySteps(InventoryService service, OutboxRepository outbox,
                          @Value("${local.server.port}") int port) {
        this.service = service;
        this.outbox = outbox;
        this.port = port;
    }

    @Given("product {string} has {int} units in stock")
    public void productHasUnitsInStock(String productId, int units) {
        put("/inventory/" + productId, "{\"stock\":" + units + "}");
    }

    @Given("order {string} has reserved {int} units of {string}")
    public void orderHasReserved(String orderId, int qty, String productId) {
        service.handleEvent(orderCreatedJson(UUID.randomUUID().toString(), orderId, productId, qty));
    }

    @When("an OrderCreated event arrives for order {string} reserving {int} units of {string}")
    public void orderCreatedArrives(String orderId, int qty, String productId) {
        service.handleEvent(orderCreatedJson(UUID.randomUUID().toString(), orderId, productId, qty));
    }

    @When("the OrderCreated event for order {string} reserving {int} units of {string} is delivered twice")
    public void orderCreatedTwice(String orderId, int qty, String productId) {
        String json = orderCreatedJson("dup-oc-" + orderId, orderId, productId, qty);
        service.handleEvent(json);
        service.handleEvent(json);
    }

    @When("a ReleaseStock command arrives for order {string}")
    public void releaseStockArrives(String orderId) {
        service.handleEvent(releaseStockJson(UUID.randomUUID().toString(), orderId));
    }

    @When("the ReleaseStock command for order {string} is delivered twice")
    public void releaseStockTwice(String orderId) {
        String json = releaseStockJson("dup-rs-" + orderId, orderId);
        service.handleEvent(json);
        service.handleEvent(json);
    }

    @When("{int} OrderCreated events each reserve 1 unit of {string} concurrently")
    public void concurrentReservations(int count, String productId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int n = i;
                futures.add(pool.submit(() -> service.handleEvent(
                        orderCreatedJson(UUID.randomUUID().toString(), "C" + n, productId, 1))));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
    }

    @Then("a StockReserved event is published for order {string}")
    public void stockReservedPublished(String orderId) {
        assertOutboxContains("StockReserved", orderId);
    }

    @Then("a StockReservationFailed event is published for order {string}")
    public void stockReservationFailedPublished(String orderId) {
        assertOutboxContains("StockReservationFailed", orderId);
    }

    @Then("a StockReleased event is published for order {string}")
    public void stockReleasedPublished(String orderId) {
        assertOutboxContains("StockReleased", orderId);
    }

    @Then("the available stock of {string} is {int}")
    public void availableStockIs(String productId, int expected) {
        assertThat(get("/inventory/" + productId).path("available").asInt()).isEqualTo(expected);
    }

    @Then("stock is reserved only once")
    public void stockReservedOnce() {
        assertThat(countOutbox("StockReserved")).isEqualTo(1L);
    }

    @Then("exactly {int} reservations succeed")
    public void exactlyReservationsSucceed(int n) {
        assertThat(countOutbox("StockReserved")).isEqualTo((long) n);
    }

    // ---- helpers ----

    private void assertOutboxContains(String type, String orderId) {
        boolean found = outbox.findAll().stream()
                .anyMatch(o -> o.getType().equals(type) && payloadOrderId(o.getPayload()).equals(orderId));
        assertThat(found).as("%s for order %s in outbox", type, orderId).isTrue();
    }

    private long countOutbox(String type) {
        return outbox.findAll().stream().filter(o -> o.getType().equals(type)).count();
    }

    private String payloadOrderId(String payload) {
        try {
            return MAPPER.readTree(payload).path("orderId").asText();
        } catch (Exception e) {
            return "";
        }
    }

    private String orderCreatedJson(String eventId, String orderId, String productId, int qty) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"OrderCreated\",\"orderId\":\"" + orderId
                + "\",\"productId\":\"" + productId + "\",\"quantity\":" + qty + "}";
    }

    private String releaseStockJson(String eventId, String orderId) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"ReleaseStock\",\"orderId\":\"" + orderId + "\"}";
    }

    private JsonNode get(String path) {
        try {
            HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return MAPPER.readTree(r.body());
        } catch (Exception e) {
            throw new RuntimeException("GET " + path + " failed", e);
        }
    }

    private void put(String path, String body) {
        try {
            http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("PUT " + path + " failed", e);
        }
    }
}

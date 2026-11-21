package blush_backend;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderController(
            OrderRepository orderRepository,
            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @PostMapping
    public Order createOrder(@RequestBody Map<String, Object> data) {
        try {
            Order order = new Order();

            order.setCustomerName((String) data.get("name"));
            order.setEmail((String) data.get("email"));
            order.setPhone((String) data.get("phone"));
            order.setAddress((String) data.get("address"));

            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) data.get("items");

            if (items == null || items.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Корзина пуста");
            }

            List<Map<String, Object>> savedItems = new ArrayList<>();
            double total = 0;

            for (Map<String, Object> item : items) {
                Long productId = Long.valueOf(
                        item.get("productId").toString());

                int quantity = Integer.parseInt(
                        item.get("quantity").toString());

                if (quantity < 1) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Некорректное количество");
                }

                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Товар не найден"));

                if (!product.isAvailable()
                        || "alcoholic".equals(product.getCategory())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Этот товар недоступен");
                }

                double itemTotal = product.getPrice() * quantity;
                total += itemTotal;

                Map<String, Object> savedItem = new HashMap<>();
                savedItem.put("name", product.getName());
                savedItem.put("price", product.getPrice());
                savedItem.put("quantity", quantity);
                savedItem.put("itemTotal", itemTotal);

                savedItems.add(savedItem);
            }

            order.setOrderNumber(
                    "BC-" + UUID.randomUUID().toString()
                            .substring(0, 6).toUpperCase());

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < savedItems.size(); i++) {
                Map<String, Object> item = savedItems.get(i);

                if (i > 0) json.append(",");

                json.append("{")
                    .append("\"name\":\"").append(escapeJson(item.get("name").toString())).append("\",")
                    .append("\"price\":").append(item.get("price")).append(",")
                    .append("\"quantity\":").append(item.get("quantity")).append(",")
                    .append("\"itemTotal\":").append(item.get("itemTotal"))
                    .append("}");
            }
            json.append("]");

            order.setItemsJson(json.toString());
            order.setTotal(total);
            order.setStatus("NEW");
            order.setCreatedAt(LocalDateTime.now());

            return orderRepository.save(order);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Не удалось оформить заказ");
        }
    }

    @GetMapping
    public List<Order> getOrders() {
        return orderRepository.findAll();
    }

    @PatchMapping("/{id}/status")
    public Order updateStatus(
        @PathVariable Long id,
        @RequestBody Map<String, String> data) {

            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Заказ не найден"));

            String status = data.get("status");

            List<String> allowedStatuses = List.of(
                    "NEW", "PROCESSING", "SHIPPED", "COMPLETED"
            );

            if (!allowedStatuses.contains(status)) {
                throw new RuntimeException("Некорректный статус");
            }

            order.setStatus(status);

            return orderRepository.save(order);
        }

    @DeleteMapping("/{id}")
    public void deleteOrder(@PathVariable Long id) {
        orderRepository.deleteById(id);
    }

    private String escapeJson(String value) {
    return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
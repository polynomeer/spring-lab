package lab.sampleapp.orderplatform.order;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(OrderOutboxEvent event) {
        jdbcTemplate.update(
                "INSERT INTO order_outbox_events (id, order_id, event_type, payload, published) "
                        + "VALUES (?, ?, ?, ?, ?)",
                event.id(), event.orderId(), event.eventType(), event.payload(), event.published());
    }

    public List<OrderOutboxEvent> findByOrderId(long orderId) {
        return jdbcTemplate.query(
                "SELECT id, order_id, event_type, payload, published FROM order_outbox_events WHERE order_id = ?",
                (rs, rowNum) -> new OrderOutboxEvent(
                        rs.getLong("id"), rs.getLong("order_id"), rs.getString("event_type"),
                        rs.getString("payload"), rs.getBoolean("published")),
                orderId);
    }

    public List<OrderOutboxEvent> findUnpublished() {
        return jdbcTemplate.query(
                "SELECT id, order_id, event_type, payload, published FROM order_outbox_events WHERE published = FALSE",
                (rs, rowNum) -> new OrderOutboxEvent(
                        rs.getLong("id"), rs.getLong("order_id"), rs.getString("event_type"),
                        rs.getString("payload"), rs.getBoolean("published")));
    }

    public void markPublished(long id) {
        jdbcTemplate.update("UPDATE order_outbox_events SET published = TRUE WHERE id = ?", id);
    }
}

package lab.sampleapp.outbox;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(OutboxEvent event) {
        jdbcTemplate.update(
                "INSERT INTO outbox_events (id, order_id, payload, published) VALUES (?, ?, ?, ?)",
                event.id(), event.orderId(), event.payload(), event.published());
    }

    public List<OutboxEvent> findUnpublished() {
        return jdbcTemplate.query(
                "SELECT id, order_id, payload, published FROM outbox_events WHERE published = FALSE",
                (rs, rowNum) -> new OutboxEvent(
                        rs.getLong("id"), rs.getLong("order_id"), rs.getString("payload"), rs.getBoolean("published")));
    }

    public void markPublished(long id) {
        jdbcTemplate.update("UPDATE outbox_events SET published = TRUE WHERE id = ?", id);
    }

    public boolean isPublished(long id) {
        Boolean published = jdbcTemplate.queryForObject(
                "SELECT published FROM outbox_events WHERE id = ?", Boolean.class, id);
        return Boolean.TRUE.equals(published);
    }
}

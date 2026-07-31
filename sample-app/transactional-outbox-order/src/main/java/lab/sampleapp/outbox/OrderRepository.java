package lab.sampleapp.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(Order order) {
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_name, amount) VALUES (?, ?, ?)",
                order.id(), order.customerName(), order.amount());
    }

    public boolean existsById(long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }
}

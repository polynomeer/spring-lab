package lab.sampleapp.orderplatform.order;

import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
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
                "INSERT INTO orders (id, member_id, amount_won, status) VALUES (?, ?, ?, ?)",
                order.id(), order.memberId(), order.amountWon(), order.status().name());
    }

    public void updateStatus(long id, OrderStatus status) {
        jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?", status.name(), id);
    }

    public Optional<Order> findById(long id) {
        try {
            Order order = jdbcTemplate.queryForObject(
                    "SELECT id, member_id, amount_won, status FROM orders WHERE id = ?",
                    (rs, rowNum) -> new Order(
                            rs.getLong("id"), rs.getString("member_id"), rs.getLong("amount_won"),
                            OrderStatus.valueOf(rs.getString("status"))),
                    id);
            return Optional.ofNullable(order);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}

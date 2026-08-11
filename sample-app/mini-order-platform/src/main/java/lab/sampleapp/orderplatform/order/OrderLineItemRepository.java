package lab.sampleapp.orderplatform.order;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderLineItemRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderLineItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(OrderLineItem item) {
        jdbcTemplate.update(
                "INSERT INTO order_line_items (id, order_id, product_id, quantity, unit_price_won) "
                        + "VALUES (?, ?, ?, ?, ?)",
                item.id(), item.orderId(), item.productId(), item.quantity(), item.unitPriceWon());
    }

    public List<OrderLineItem> findByOrderId(long orderId) {
        return jdbcTemplate.query(
                "SELECT id, order_id, product_id, quantity, unit_price_won FROM order_line_items WHERE order_id = ?",
                (rs, rowNum) -> new OrderLineItem(
                        rs.getLong("id"), rs.getLong("order_id"), rs.getLong("product_id"),
                        rs.getInt("quantity"), rs.getLong("unit_price_won")),
                orderId);
    }
}

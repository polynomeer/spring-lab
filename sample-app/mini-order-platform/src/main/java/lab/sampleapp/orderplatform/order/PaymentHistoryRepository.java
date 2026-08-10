package lab.sampleapp.orderplatform.order;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lab.sampleapp.orderplatform.payment.PaymentMethod;

@Repository
public class PaymentHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(PaymentHistoryEntry entry) {
        jdbcTemplate.update(
                "INSERT INTO payment_history (id, order_id, method, amount_won, success, transaction_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                entry.id(), entry.orderId(), entry.method().name(), entry.amountWon(),
                entry.success(), entry.transactionId());
    }

    public List<PaymentHistoryEntry> findByOrderId(long orderId) {
        return jdbcTemplate.query(
                "SELECT id, order_id, method, amount_won, success, transaction_id "
                        + "FROM payment_history WHERE order_id = ?",
                (rs, rowNum) -> new PaymentHistoryEntry(
                        rs.getLong("id"), rs.getLong("order_id"),
                        PaymentMethod.valueOf(rs.getString("method")), rs.getLong("amount_won"),
                        rs.getBoolean("success"), rs.getString("transaction_id")),
                orderId);
    }
}

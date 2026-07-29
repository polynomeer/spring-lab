package lab.experiments.tx;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerRepository {

    private final JdbcTemplate jdbcTemplate;

    public LedgerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String event) {
        jdbcTemplate.update("INSERT INTO ledger (event) VALUES (?)", event);
    }

    public boolean contains(String event) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger WHERE event = ?", Integer.class, event);
        return count != null && count > 0;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM ledger");
    }
}

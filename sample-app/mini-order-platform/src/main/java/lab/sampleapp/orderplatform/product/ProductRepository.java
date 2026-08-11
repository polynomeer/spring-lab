package lab.sampleapp.orderplatform.product;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(Product product) {
        jdbcTemplate.update(
                "INSERT INTO products (id, name, price_won, stock) VALUES (?, ?, ?, ?)",
                product.id(), product.name(), product.priceWon(), product.stock());
    }

    public Optional<Product> findById(long id) {
        try {
            Product product = jdbcTemplate.queryForObject(
                    "SELECT id, name, price_won, stock FROM products WHERE id = ?",
                    (rs, rowNum) -> new Product(
                            rs.getLong("id"), rs.getString("name"), rs.getLong("price_won"), rs.getInt("stock")),
                    id);
            return Optional.ofNullable(product);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<Product> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, price_won, stock FROM products ORDER BY id",
                (rs, rowNum) -> new Product(
                        rs.getLong("id"), rs.getString("name"), rs.getLong("price_won"), rs.getInt("stock")));
    }

    /**
     * 재고를 원자적으로 줄인다 - UPDATE 문 자체에 "충분한 재고가 있을 때만" 조건을 걸어서
     * (WHERE stock &gt;= quantity), "재고 조회 → 자바에서 뺄셈 → 다시 저장"처럼 조회와 갱신
     * 사이에 다른 트랜잭션이 끼어들 틈을 주지 않는다. 영향받은 행이 0이면 재고 부족이라는
     * 뜻이고, 그 판단 자체가 DB 엔진 수준에서 원자적으로 이뤄진다 - 애플리케이션 코드에서
     * 별도 락을 잡을 필요가 없다.
     */
    public boolean decreaseStock(long productId, int quantity) {
        int updatedRows = jdbcTemplate.update(
                "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?",
                quantity, productId, quantity);
        return updatedRows == 1;
    }
}

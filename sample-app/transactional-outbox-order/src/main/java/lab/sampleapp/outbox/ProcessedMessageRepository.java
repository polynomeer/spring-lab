package lab.sampleapp.outbox;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// 컨슈머 쪽 멱등성 - "이 메시지 ID를 이미 처리했는가"를 DB에 durable하게 기록해서, 같은
// 메시지가 두 번 배달돼도(at-least-once 브로커의 재전송) 비즈니스 로직이 두 번 실행되지
// 않게 막는다.
@Repository
public class ProcessedMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 처음 보는 메시지면 true(그리고 기록), 이미 처리한 메시지면 false.
    public boolean markProcessedIfNew(long messageId) {
        try {
            jdbcTemplate.update("INSERT INTO processed_messages (message_id) VALUES (?)", messageId);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }
}

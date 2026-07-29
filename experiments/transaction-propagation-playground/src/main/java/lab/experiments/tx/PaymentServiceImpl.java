package lab.experiments.tx;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final LedgerRepository ledgerRepository;
    private final DataSource dataSource;

    public PaymentServiceImpl(LedgerRepository ledgerRepository, DataSource dataSource) {
        this.ledgerRepository = ledgerRepository;
        this.dataSource = dataSource;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void payRequired(boolean fail) {
        ledgerRepository.record("payment");
        PropagationLog.capture("payment", dataSource);
        if (fail) {
            throw new PaymentFailedException("payment (REQUIRED) failed");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void payRequiresNew(boolean fail) {
        ledgerRepository.record("payment");
        PropagationLog.capture("payment", dataSource);
        if (fail) {
            throw new PaymentFailedException("payment (REQUIRES_NEW) failed");
        }
    }

    @Override
    @Transactional(propagation = Propagation.NESTED)
    public void payNested(boolean fail) {
        ledgerRepository.record("payment");
        PropagationLog.capture("payment", dataSource);
        if (fail) {
            throw new PaymentFailedException("payment (NESTED) failed");
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void payNotSupported(boolean fail) {
        ledgerRepository.record("payment");
        PropagationLog.capture("payment", dataSource);
        if (fail) {
            throw new PaymentFailedException("payment (NOT_SUPPORTED) failed");
        }
    }
}

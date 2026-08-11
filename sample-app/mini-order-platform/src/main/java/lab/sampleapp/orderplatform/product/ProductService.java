package lab.sampleapp.orderplatform.product;

import org.springframework.stereotype.Service;

import lab.sampleapp.orderplatform.aop.Audited;
import lab.sampleapp.orderplatform.aop.RequiresRole;
import lab.sampleapp.orderplatform.aop.Role;
import lab.sampleapp.orderplatform.aop.Timed;
import lab.sampleapp.orderplatform.order.IdGenerator;

/**
 * 상품 등록은 관리자만 할 수 있다 - Phase 2에서 이미 만든 AOP 스택(@RequiresRole/@Audited/@Timed)을
 * 새 도메인에 그대로 재사용한다. PaymentProcessingService.refund()와 같은 이유로 별도
 * 서비스 빈으로 분리했다 - 컨트롤러가 이 메서드를 직접 호출해야 프록시를 거쳐 어드바이스가
 * 적용된다.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final IdGenerator idGenerator;

    public ProductService(ProductRepository productRepository, IdGenerator idGenerator) {
        this.productRepository = productRepository;
        this.idGenerator = idGenerator;
    }

    @Timed
    @Audited(action = "create-product")
    @RequiresRole(Role.ADMIN)
    public Product createProduct(String name, long priceWon, int stock) {
        Product product = new Product(idGenerator.nextId(), name, priceWon, stock);
        productRepository.save(product);
        return product;
    }
}

package lab.sampleapp.orderplatform.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lab.sampleapp.orderplatform.aop.CurrentActor;
import lab.sampleapp.orderplatform.product.Product;
import lab.sampleapp.orderplatform.product.ProductNotFoundException;
import lab.sampleapp.orderplatform.product.ProductRepository;
import lab.sampleapp.orderplatform.product.ProductService;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductService productService;

    public ProductController(ProductRepository productRepository, ProductService productService) {
        this.productRepository = productRepository;
        this.productService = productService;
    }

    @GetMapping
    public List<Product> listProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    // actor 파라미터 값 자체는 쓰지 않지만, @CurrentMember가 실행돼야 CurrentActor가 채워져서
    // ProductService.createProduct()의 @RequiresRole(ADMIN) 검사가 그 값을 볼 수 있다.
    @PostMapping
    public Product createProduct(@CurrentMember CurrentActor.Actor actor, @RequestBody CreateProductRequest request) {
        return productService.createProduct(request.name(), request.priceWon(), request.stock());
    }
}

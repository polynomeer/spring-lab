package lab.sampleapp.orderplatform.web;

public record CreateProductRequest(String name, long priceWon, int stock) {
}

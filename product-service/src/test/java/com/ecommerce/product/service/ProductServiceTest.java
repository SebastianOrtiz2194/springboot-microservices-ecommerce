package com.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

/** Unit tests for {@link ProductService}. */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;

    @Mock private S3Service s3Service;

    @InjectMocks private ProductService productService;

    private Product product(Long id) {
        Product product = new Product("Laptop", "A laptop", new BigDecimal("999.99"));
        product.setId(id);
        product.setStockQuantity(10);
        return product;
    }

    @Test
    void getProduct_returnsProductWhenFound() {
        Product product = product(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Product result = productService.getProduct(1L);

        assertThat(result).isEqualTo(product);
    }

    @Test
    void getProduct_throwsWhenNotFound() {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(42L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void createProduct_savesAndReturnsProduct() {
        Product product = product(null);
        when(productRepository.save(product)).thenReturn(product);

        Product result = productService.createProduct(product);

        assertThat(result).isSameAs(product);
        verify(productRepository).save(product);
    }

    @Test
    void getAllProducts_returnsCatalog() {
        List<Product> products = List.of(product(1L), product(2L));
        when(productRepository.findAll()).thenReturn(products);

        List<Product> result = productService.getAllProducts();

        assertThat(result).containsExactlyElementsOf(products);
    }

    @Test
    void uploadProductImage_throwsWhenProductNotFound() throws IOException {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);

        assertThatThrownBy(() -> productService.uploadProductImage(42L, file))
                .isInstanceOf(ProductNotFoundException.class);

        verify(s3Service, org.mockito.Mockito.never())
                .uploadImage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void uploadProductImage_storesKeyAndReturnsPresignedUrl() throws IOException {
        Product product = product(1L);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(s3Service.uploadImage(file)).thenReturn("s3-key-1");
        when(s3Service.createPresignedUrl("s3-key-1")).thenReturn("https://s3.example/s3-key-1");
        when(productRepository.save(product)).thenReturn(product);

        String url = productService.uploadProductImage(1L, file);

        assertThat(url).isEqualTo("https://s3.example/s3-key-1");
        assertThat(product.getImageUrl()).isEqualTo("s3-key-1");
        verify(productRepository).save(product);
    }

    @Test
    void resolveImageUrl_delegatesToS3Service() {
        when(s3Service.createPresignedUrl("s3-key-1")).thenReturn("https://s3.example/s3-key-1");

        String result = productService.resolveImageUrl("s3-key-1");

        assertThat(result).isEqualTo("https://s3.example/s3-key-1");
    }
}

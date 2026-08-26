package com.ecommerce.product.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.product.domain.ProcessedOrder;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.repository.ProcessedOrderRepository;
import com.ecommerce.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** Unit tests for {@link OrderEventConsumer}. */
@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock private ProductRepository productRepository;

    @Mock private ProcessedOrderRepository processedOrderRepository;

    @InjectMocks private OrderEventConsumer consumer;

    private OrderPlacedEvent event(Long orderId, OrderItemEvent... items) {
        return new OrderPlacedEvent(
                orderId, 7L, List.of(items), BigDecimal.TEN, LocalDateTime.now());
    }

    private Product productWithStock(int stock) {
        Product product = new Product("Laptop", "A laptop", BigDecimal.TEN);
        product.setStockQuantity(stock);
        return product;
    }

    @Test
    void handleOrderPlaced_skipsDuplicateOrder() {
        OrderPlacedEvent event = event(1L, new OrderItemEvent(10L, 2, BigDecimal.TEN));
        when(processedOrderRepository.existsById(1L)).thenReturn(true);

        consumer.handleOrderPlaced(event);

        verify(productRepository, never()).decrementStock(anyLong(), anyInt());
        verify(processedOrderRepository, never()).save(any(ProcessedOrder.class));
    }

    @Test
    void handleOrderPlaced_decrementsStockAndMarksProcessed() {
        OrderPlacedEvent event = event(1L, new OrderItemEvent(10L, 2, BigDecimal.TEN));
        when(processedOrderRepository.existsById(1L)).thenReturn(false);
        when(productRepository.decrementStock(10L, 2)).thenReturn(1);

        consumer.handleOrderPlaced(event);

        verify(productRepository).decrementStock(10L, 2);
        ArgumentCaptor<ProcessedOrder> captor = ArgumentCaptor.forClass(ProcessedOrder.class);
        verify(processedOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(1L);
    }

    @Test
    void handleOrderPlaced_skipsInvalidQuantity() {
        OrderPlacedEvent event = event(1L, new OrderItemEvent(10L, 0, BigDecimal.TEN));
        when(processedOrderRepository.existsById(1L)).thenReturn(false);

        consumer.handleOrderPlaced(event);

        verify(productRepository, never()).decrementStock(anyLong(), anyInt());
        verify(processedOrderRepository).save(any(ProcessedOrder.class));
    }

    @Test
    void handleOrderPlaced_skipsMissingProduct() {
        OrderPlacedEvent event = event(1L, new OrderItemEvent(99L, 2, BigDecimal.TEN));
        when(processedOrderRepository.existsById(1L)).thenReturn(false);
        when(productRepository.decrementStock(99L, 2)).thenReturn(0);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        consumer.handleOrderPlaced(event);

        verify(productRepository).decrementStock(99L, 2);
        verify(productRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void handleOrderPlaced_keepsStockWhenInsufficient() {
        OrderPlacedEvent event = event(1L, new OrderItemEvent(10L, 100, BigDecimal.TEN));
        Product product = productWithStock(5);
        when(processedOrderRepository.existsById(1L)).thenReturn(false);
        when(productRepository.decrementStock(10L, 100)).thenReturn(0);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        consumer.handleOrderPlaced(event);

        assertThat(product.getStockQuantity()).isEqualTo(5);
        verify(productRepository, never()).findByIdForUpdate(anyLong());
        verify(productRepository, never()).save(product);
    }

    @Test
    void handleOrderPlaced_fallsBackToPessimisticLockOnVersionConflict() {
        OrderPlacedEvent event = event(1L, new OrderItemEvent(10L, 2, BigDecimal.TEN));
        Product product = productWithStock(10);
        Product locked = productWithStock(10);
        when(processedOrderRepository.existsById(1L)).thenReturn(false);
        when(productRepository.decrementStock(10L, 2)).thenReturn(0);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(locked));

        consumer.handleOrderPlaced(event);

        verify(productRepository).save(locked);
        assertThat(locked.getStockQuantity()).isEqualTo(8);
    }

    @Test
    void handleOrderPlaced_rethrowsDuplicateOnConcurrentInsert() {
        OrderPlacedEvent event = event(1L, new OrderItemEvent(10L, 2, BigDecimal.TEN));
        when(processedOrderRepository.existsById(1L)).thenReturn(false);
        when(productRepository.decrementStock(10L, 2)).thenReturn(1);
        when(processedOrderRepository.save(any(ProcessedOrder.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> consumer.handleOrderPlaced(event))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

package com.hengtongan.computerstore;

import com.hengtongan.computerstore.core.repository.CartRepository;
import com.hengtongan.computerstore.core.repository.InventoryRepository;
import com.hengtongan.computerstore.core.repository.OrderRepository;
import com.hengtongan.computerstore.core.repository.ProductRepository;
import com.hengtongan.computerstore.core.exception.InsufficientStockException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.CartItem;
import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceCheckoutTest {

    @Mock
    private CartRepository cartDAO;
    @Mock
    private ProductRepository productDAO;
    @Mock
    private OrderRepository orderDAO;
    @Mock
    private InventoryRepository inventoryDAO;

    private OrderService orderService;
    private User customer;
    private Connection connection;

    @BeforeEach
    void setUp() {
        connection = mock(Connection.class);
        orderService = new OrderService(cartDAO, productDAO, orderDAO, inventoryDAO,
                () -> connection);
        customer = new User();
        customer.setUserId(3);
        customer.setUsername("customer");
    }

    @Test
    void checkoutRejectsEmptyCart() throws Exception {
        when(cartDAO.findItemsByUser(3)).thenReturn(Collections.emptyList());

        ValidationException ex = assertThrows(ValidationException.class,
                () -> orderService.checkout(customer));
        assertEquals("Your cart is empty.", ex.getMessage());
        verify(orderDAO, never()).createOrder(any(), any());
    }

    @Test
    void checkoutThrowsWhenStockInsufficient() throws Exception {
        Product product = new Product();
        product.setProductId(10);
        product.setName("GPU");
        product.setPrice(new BigDecimal("199.00"));
        product.setStockQuantity(1);
        product.setStatus(Product.Status.LOW_STOCK);

        CartItem item = new CartItem();
        item.setProductId(10);
        item.setQuantity(2);
        item.setProduct(product);

        when(cartDAO.findItemsByUser(3)).thenReturn(List.of(item));

        when(productDAO.reduceStock(eq(connection), eq(10), eq(2))).thenReturn(false);

        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> orderService.checkout(customer));
        assertTrueMessage(ex.getMessage(), "GPU");
        verify(connection).rollback();
        verify(orderDAO, never()).createOrder(any(), any());
    }

    @Test
    void cancelledOrderCannotBeReopenedBecauseItsStockWasRestored() {
        Order cancelled = new Order();
        cancelled.setOrderId(7);
        cancelled.setStatus(Order.Status.CANCELLED);
        when(orderDAO.findById(7)).thenReturn(cancelled);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> orderService.updateStatus(7, Order.Status.PROCESSING));

        assertTrueMessage(ex.getMessage(), "cannot change");
        verify(orderDAO, never()).updateStatus(any(), anyInt(), any());
    }

    @Test
    void checkoutClearsCartBeforeCommittingTheOrderTransaction() throws Exception {
        Product product = new Product();
        product.setProductId(10);
        product.setName("GPU");
        product.setPrice(new BigDecimal("199.00"));
        product.setStockQuantity(4);
        product.setStatus(Product.Status.IN_STOCK);

        CartItem item = new CartItem();
        item.setProductId(10);
        item.setQuantity(1);
        item.setProduct(product);
        when(cartDAO.findItemsByUser(3)).thenReturn(List.of(item));
        when(productDAO.reduceStock(connection, 10, 1)).thenReturn(true);
        when(productDAO.stock(connection, 10))
                .thenReturn(new ProductRepository.StockSnapshot(3, Product.Status.IN_STOCK));
        when(orderDAO.createOrder(any(), any())).thenReturn(11);

        orderService.checkout(customer);

        org.mockito.InOrder order = inOrder(cartDAO, connection);
        order.verify(cartDAO).clear(connection, 3);
        order.verify(connection).commit();
        verify(cartDAO, never()).clear(3);
    }

    private static void assertTrueMessage(String message, String expectedPart) {
        if (message == null || !message.contains(expectedPart)) {
            throw new AssertionError("Expected message to contain '" + expectedPart + "' but was: " + message);
        }
    }
}

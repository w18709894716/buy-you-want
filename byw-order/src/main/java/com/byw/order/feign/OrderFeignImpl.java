package com.byw.order.feign;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.order.entity.Order;
import com.byw.order.entity.OrderItem;
import com.byw.order.mapper.OrderItemMapper;
import com.byw.order.mapper.OrderMapper;
import com.byw.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/feign/order")
@RequiredArgsConstructor
public class OrderFeignImpl implements OrderFeignClient {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    @PostMapping("/create")
    public R<String> createOrder(@RequestBody OrderCreateDTO createDTO) {
        String orderNo = orderService.createOrder(createDTO);
        return R.ok(orderNo);
    }

    @Override
    @GetMapping("/{orderNo}")
    public R<OrderDetailDTO> getOrderDetail(@PathVariable("orderNo") String orderNo) {
        return R.ok(orderService.getOrderDetail(orderNo));
    }

    @Override
    @PostMapping("/cancel/{orderNo}")
    public R<Boolean> cancelOrder(@PathVariable("orderNo") String orderNo) {
        orderService.cancelOrder(orderNo, "系统取消");
        return R.ok(true);
    }

    @Override
    @PostMapping("/update-status")
    public R<Boolean> updateOrderStatus(@RequestParam("orderNo") String orderNo, @RequestParam("status") Integer status) {
        orderService.updateStatus(orderNo, status);
        return R.ok(true);
    }

    @Override
    @GetMapping("/list")
    public R<PageResult<OrderDetailDTO>> listOrders(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                    @RequestParam(value = "status", required = false) Integer status,
                                                    @RequestParam(value = "orderNo", required = false) String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.like(Order::getOrderNo, orderNo);
        }
        wrapper.orderByDesc(Order::getCreatedAt);

        IPage<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<OrderDetailDTO> dtoList = page.getRecords().stream().map(order -> {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, order.getOrderNo()));
            return buildOrderDetailDTO(order, items);
        }).collect(Collectors.toList());

        return R.ok(PageResult.of(dtoList, page.getTotal(), pageNum, pageSize));
    }

    @Override
    @GetMapping("/stats")
    public R<OrderStatsDTO> getOrderStats() {
        Long totalOrders = orderMapper.selectCount(null);
        BigDecimal totalRevenue = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .in(Order::getStatus, 1, 2, 3)) // 已付款、已发货、已完成
                .stream()
                .map(Order::getPayAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Long pendingOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 0)); // 待付款

        OrderStatsDTO stats = new OrderStatsDTO();
        stats.setTotalOrders(totalOrders);
        stats.setTotalRevenue(totalRevenue);
        stats.setPendingOrders(pendingOrders);
        return R.ok(stats);
    }

    @Override
    @GetMapping("/today-stats")
    public R<TodayOrderStatsDTO> getTodayOrderStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .ge(Order::getCreatedAt, todayStart);

        List<Order> todayOrders = orderMapper.selectList(wrapper);
        Long todayOrderCount = (long) todayOrders.size();
        BigDecimal todayRevenue = todayOrders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() >= 1) // 已付款
                .map(Order::getPayAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        TodayOrderStatsDTO stats = new TodayOrderStatsDTO();
        stats.setTodayOrders(todayOrderCount);
        stats.setTodayRevenue(todayRevenue);
        return R.ok(stats);
    }

    @Override
    @PostMapping("/update-reviewed")
    public R<Boolean> updateReviewed(@RequestParam("orderNo") String orderNo, @RequestParam("reviewed") Integer reviewed) {
        orderService.updateReviewed(orderNo, reviewed);
        return R.ok(true);
    }

    private OrderDetailDTO buildOrderDetailDTO(Order order, List<OrderItem> items) {
        OrderDetailDTO dto = new OrderDetailDTO();
        BeanUtils.copyProperties(order, dto);

        List<OrderDetailDTO.OrderItemDTO> itemDTOs = items.stream().map(item -> {
            OrderDetailDTO.OrderItemDTO itemDTO = new OrderDetailDTO.OrderItemDTO();
            BeanUtils.copyProperties(item, itemDTO);
            return itemDTO;
        }).collect(Collectors.toList());
        dto.setItems(itemDTOs);

        return dto;
    }
}

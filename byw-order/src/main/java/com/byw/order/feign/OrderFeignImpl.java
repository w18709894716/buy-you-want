package com.byw.order.feign;

import com.byw.common.log.annotation.OperationLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.exception.ResultCode;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.InternalApi;
import com.byw.common.security.context.UserContext;
import com.byw.order.entity.Order;
import com.byw.order.entity.OrderItem;
import com.byw.order.mapper.OrderItemMapper;
import com.byw.order.mapper.OrderMapper;
import com.byw.order.service.OrderService;
import jakarta.validation.Valid;
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
@InternalApi
public class OrderFeignImpl implements OrderFeignClient {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    @PostMapping("/create")
    public R<String> createOrder(@Valid @RequestBody OrderCreateDTO createDTO) {
        String orderNo = orderService.createOrder(createDTO);
        return R.ok(orderNo);
    }

    @Override
    @GetMapping("/{orderNo}")
    public R<OrderDetailDTO> getOrderDetail(@PathVariable("orderNo") String orderNo) {
        requireShopAccess(orderNo);
        return R.ok(orderService.getOrderDetail(orderNo));
    }

    @Override
    @PostMapping("/cancel/{orderNo}")
    public R<Boolean> cancelOrder(@PathVariable("orderNo") String orderNo) {
        requireShopAccess(orderNo);
        orderService.cancelOrder(orderNo, "系统取消");
        return R.ok(true);
    }

    @Override
    @PostMapping("/update-status")
    public R<Boolean> updateOrderStatus(@RequestParam("orderNo") String orderNo, @RequestParam("status") Integer status) {
        requireShopAccess(orderNo);
        orderService.updateStatus(orderNo, status);
        return R.ok(true);
    }

    @Override
    @OperationLog(module = "订单", operation = "发货", description = "商家发货（按商品行）")
    @PostMapping("/ship-items")
    public R<Boolean> shipItems(@RequestParam("orderNo") String orderNo,
                                @RequestParam("companyName") String companyName,
                                @RequestParam(value = "trackingNo", required = false) String trackingNo,
                                @RequestBody List<Long> itemIds) {
        requireShopAccess(orderNo);
        orderService.shipItems(orderNo, itemIds, companyName, trackingNo);
        return R.ok(true);
    }

    @Override
    @GetMapping("/list")
    public R<PageResult<OrderDetailDTO>> listOrders(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                    @RequestParam(value = "statuses", required = false) List<Integer> statuses,
                                                    @RequestParam(value = "orderNo", required = false) String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        // 商家上下文：仅返回本店订单；平台管理员 shopId 为空则不过滤
        Long shopId = UserContext.getShopId();
        wrapper.eq(shopId != null, Order::getShopId, shopId);
        // 仅返回子订单/独立订单，排除仅聚合支付的父订单
        wrapper.eq(Order::getIsParent, 0);
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(Order::getStatus, statuses);
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
        Long totalOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getIsParent, 0)); // 仅统计子订单/独立订单，排除父订单避免重复计数
        BigDecimal totalRevenue = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .eq(Order::getIsParent, 0)
                        .in(Order::getStatus, 1, 2, 3)) // 已付款、已发货、已完成
                .stream()
                .map(Order::getPayAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Long pendingOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getIsParent, 0)
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
                .eq(Order::getIsParent, 0) // 排除父订单避免重复计数
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

    @Override
    @GetMapping("/count-by-user")
    public R<Long> countUserOrders(@RequestParam("userId") Long userId) {
        Long count = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getUserId, userId));
        return R.ok(count);
    }

    /**
     * 商家上下文的租户校验：X-Shop-Id 非空时只能操作本店订单。
     *
     * <p>按 orderNo 读写的端点原先只有 list 做了店铺过滤，detail/发货没有——
     * 商家 A 的员工凭 m:order:* 权限就能读商家 B 的订单详情（含买家收货信息）并替其发货。
     *
     * <p>口径与 listOrders 一致：shopId 为空表示平台管理员、C 端用户或后台任务
     * （库存回补、结算、支付回调、IM 分流都没有店铺上下文），不做限制。
     * 因此这里**不能**用 {@code UserContext.canAccessShop}——那个方法对"无店铺上下文且非管理员"
     * 一律拒绝，会把服务间调用与定时任务全部打死。
     *
     * <p>订单不存在时不在此处报错，交给下游按原有语义返回"订单不存在"。
     */
    private void requireShopAccess(String orderNo) {
        Long ctxShopId = UserContext.getShopId();
        if (ctxShopId == null || ctxShopId <= 0) {
            return;
        }
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order != null && !ctxShopId.equals(order.getShopId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
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

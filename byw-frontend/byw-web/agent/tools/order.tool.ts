/**
 * 订单域工具：列表、详情、取消、确认收货、下单。
 * 下单为 critical（产生真实交易单据），需人工确认。
 */
import type { ToolDefinition } from '~/agent/sdk/index.js'
import { get, post } from '~/utils/request'
import { num, numOr, optionalNum, optionalStr, str } from '~/agent/tool-utils'

/** 订单状态码 → 文案（与后端 /order/my-orders 的 status 参数一致） */
const ORDER_STATUS = '0=待付款, 1=待发货, 2=待收货, 3=待评价(已完成), 4=交易关闭, 5=退款中'

export default [
  {
    name: 'list_my_orders',
    description: `查询当前用户的订单列表，可按状态筛选（状态码：${ORDER_STATUS}）`,
    riskLevel: 'read',
    parameters: {
      type: 'object',
      properties: {
        status: { type: 'number', description: `订单状态码：${ORDER_STATUS}；不传表示全部` },
        pageNum: { type: 'number', description: '页码，默认 1' },
        pageSize: { type: 'number', description: '每页条数，默认 10' },
      },
    },
    handler: async (params) => {
      const data = await get<any>('/order/my-orders', {
        pageNum: numOr(params, 'pageNum', 1),
        pageSize: numOr(params, 'pageSize', 10),
        status: optionalNum(params, 'status'),
      })
      return {
        total: data?.total ?? 0,
        list: (data?.list || []).map((o: any) => ({
          orderNo: o.orderNo,
          status: o.status,
          statusText: o.statusText,
          payAmount: o.payAmount,
          createTime: o.createTime,
          items: (o.items || o.orderItems || []).map((i: any) => ({
            productName: i.productName,
            skuName: i.skuName,
            quantity: i.quantity,
          })),
        })),
      }
    },
  },

  {
    name: 'get_order_detail',
    description: '根据订单号获取订单详情（商品明细、收货地址、金额、状态、物流）',
    riskLevel: 'read',
    parameters: {
      type: 'object',
      properties: { orderNo: { type: 'string', description: '订单号' } },
      required: ['orderNo'],
    },
    handler: (params) => get(`/order/detail/${str(params, 'orderNo')}`),
  },

  {
    name: 'cancel_order',
    description: '取消指定订单（仅待付款订单可取消）',
    riskLevel: 'write',
    parameters: {
      type: 'object',
      properties: { orderNo: { type: 'string', description: '订单号' } },
      required: ['orderNo'],
    },
    handler: async (params) => {
      const orderNo = str(params, 'orderNo')
      await post(`/order/cancel/${orderNo}`)
      return { ok: true, orderNo, action: 'cancelled' }
    },
  },

  {
    name: 'confirm_receipt',
    description: '确认收货（仅待收货订单可确认），确认后订单流转为已完成',
    riskLevel: 'write',
    parameters: {
      type: 'object',
      properties: { orderNo: { type: 'string', description: '订单号' } },
      required: ['orderNo'],
    },
    handler: async (params) => {
      const orderNo = str(params, 'orderNo')
      await post(`/order/confirm/${orderNo}`)
      return { ok: true, orderNo, action: 'confirmed' }
    },
  },

  {
    name: 'create_order_from_cart',
    description:
      '把购物车中的全部商品提交为订单（高风险：产生真实订单并锁定库存）。需要先通过 list_my_addresses 获取收货地址 ID；下单后订单为待付款状态',
    riskLevel: 'critical',
    parameters: {
      type: 'object',
      properties: {
        addressId: { type: 'number', description: '收货地址 ID' },
        couponId: { type: 'number', description: '使用的优惠券 ID，可选' },
        remark: { type: 'string', description: '订单备注，可选' },
      },
      required: ['addressId'],
    },
    handler: async (params) => {
      const addressId = num(params, 'addressId')
      const couponId = optionalNum(params, 'couponId')
      const remark = optionalStr(params, 'remark') ?? ''

      const cartItems = await get<any[]>('/cart/list')
      if (!cartItems || cartItems.length === 0) {
        throw new Error('购物车为空，无法下单')
      }

      const OrderItems = cartItems.map((item: any) => ({
        productId: item.productId,
        skuId: item.skuId,
        shopId: item.shopId,
        productName: item.productName,
        skuName: item.skuName || '默认规格',
        productImage: item.productImage || item.image,
        price: item.price,
        quantity: item.quantity,
      }))

      const orderNo = await post<string>('/order/create', {
        addressId,
        couponId: couponId ?? null,
        remark,
        items: OrderItems,
      })

      return { ok: true, orderNo, itemCount: OrderItems.length }
    },
  },
] satisfies ToolDefinition[]

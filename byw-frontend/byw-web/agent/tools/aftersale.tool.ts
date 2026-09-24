/**
 * 售后域工具：退款详情查询、申请售后。
 * 申请售后为 critical（涉及退款金额，需人工确认）。
 */
import type { ToolDefinition } from '~/agent/sdk/index.js'
import { get, post } from '~/utils/request'
import { num, optionalNum, optionalStr, str } from '~/agent/tool-utils'

/** 售后类型：1=仅退款 2=退货退款 3=换货 4=维修 5=补寄 6=价保（1/2/6 需传退款金额） */
const AFTER_SALE_TYPE = '1=仅退款, 2=退货退款, 3=换货, 4=维修, 5=补寄, 6=价保'

export default [
  {
    name: 'get_refund_detail',
    description: '查询指定订单的售后/退款详情（售后单号、进度、退款金额、退货物流）',
    riskLevel: 'read',
    parameters: {
      type: 'object',
      properties: {
        orderNo: { type: 'string', description: '订单号' },
        orderItemId: { type: 'number', description: '订单明细 ID，可选，用于查询单个商品的售后' },
      },
      required: ['orderNo'],
    },
    handler: (params) => {
      const itemId = optionalNum(params, 'orderItemId')
      return get(`/order/aftersale/refund-detail/${str(params, 'orderNo')}`, itemId != null ? { itemId } : undefined)
    },
  },

  {
    name: 'apply_after_sale',
    description: `为订单中的某个商品申请售后（高风险：涉及退款金额）。售后类型：${AFTER_SALE_TYPE}；类型为 1/2/6 时必须传 refundAmount`,
    riskLevel: 'critical',
    parameters: {
      type: 'object',
      properties: {
        orderNo: { type: 'string', description: '订单号' },
        orderItemId: { type: 'number', description: '订单明细 ID（可用 get_order_detail 获取）' },
        type: { type: 'number', description: `售后类型：${AFTER_SALE_TYPE}` },
        reason: { type: 'string', description: '售后原因' },
        description: { type: 'string', description: '问题补充说明，可选' },
        refundAmount: { type: 'number', description: '退款金额，类型为 1/2/6 时必填，不能超过该商品实付小计' },
      },
      required: ['orderNo', 'orderItemId', 'type', 'reason'],
    },
    handler: async (params) => {
      const type = num(params, 'type')
      const payload: Record<string, unknown> = {
        orderNo: str(params, 'orderNo'),
        orderItemId: num(params, 'orderItemId'),
        type,
        reason: str(params, 'reason'),
        description: optionalStr(params, 'description') ?? '',
      }
      if ([1, 2, 6].includes(type)) {
        payload.refundAmount = num(params, 'refundAmount')
      }
      await post('/order/aftersale/apply', payload)
      return { ok: true, orderNo: payload.orderNo, orderItemId: payload.orderItemId, type }
    },
  },
] satisfies ToolDefinition[]

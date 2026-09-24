/**
 * 购物车域工具：查看、加购、改数量、删除。批量清空为 critical（影响多个条目）。
 */
import type { ToolDefinition } from '~/agent/sdk/index.js'
import { del, get, post, put } from '~/utils/request'
import { num } from '~/agent/tool-utils'

export default [
  {
    name: 'get_cart',
    description: '查看当前用户的购物车内容，返回商品、规格、数量、单价与小计',
    riskLevel: 'read',
    parameters: { type: 'object', properties: {} },
    handler: async () => {
      const items = await get<any[]>('/cart/list')
      return {
        count: items?.length ?? 0,
        items: (items || []).map((item: any) => ({
          cartId: item.id,
          skuId: item.skuId,
          productId: item.productId,
          productName: item.productName,
          skuName: item.skuName,
          quantity: item.quantity,
          price: item.price,
        })),
      }
    },
  },

  {
    name: 'add_to_cart',
    description: '把指定 SKU 加入购物车；SKU 已在车内时数量累加',
    riskLevel: 'write',
    parameters: {
      type: 'object',
      properties: {
        skuId: { type: 'number', description: 'SKU ID' },
        quantity: { type: 'number', description: '数量，默认 1' },
      },
      required: ['skuId'],
    },
    handler: async (params) => {
      const skuId = num(params, 'skuId')
      const quantity = params.quantity === undefined ? 1 : num(params, 'quantity')
      await post('/cart/add', null, { params: { skuId, quantity } })
      return { ok: true, skuId, quantity }
    },
  },

  {
    name: 'update_cart_quantity',
    description: '修改购物车中某 SKU 的数量；数量传 0 等同于移出购物车',
    riskLevel: 'write',
    parameters: {
      type: 'object',
      properties: {
        skuId: { type: 'number', description: 'SKU ID' },
        quantity: { type: 'number', description: '新数量，0 表示移出购物车' },
      },
      required: ['skuId', 'quantity'],
    },
    handler: async (params) => {
      const skuId = num(params, 'skuId')
      const quantity = num(params, 'quantity')
      if (quantity <= 0) {
        await del(`/cart/${skuId}`)
        return { ok: true, skuId, removed: true }
      }
      await put('/cart/update', null, { params: { skuId, quantity } })
      return { ok: true, skuId, quantity }
    },
  },

  {
    name: 'remove_from_cart',
    description: '把指定 SKU 从购物车中删除',
    riskLevel: 'write',
    parameters: {
      type: 'object',
      properties: { skuId: { type: 'number', description: 'SKU ID' } },
      required: ['skuId'],
    },
    handler: async (params) => {
      const skuId = num(params, 'skuId')
      await del(`/cart/${skuId}`)
      return { ok: true, skuId }
    },
  },

  {
    name: 'clear_cart',
    description: '清空整个购物车（高风险：一次性删除车内所有商品）',
    riskLevel: 'critical',
    parameters: { type: 'object', properties: {} },
    handler: async () => {
      const items = await get<any[]>('/cart/list')
      const removed: number[] = []
      for (const item of items || []) {
        await del(`/cart/${item.skuId}`)
        removed.push(item.skuId)
      }
      return { ok: true, removedCount: removed.length, removedSkuIds: removed }
    },
  },
] satisfies ToolDefinition[]

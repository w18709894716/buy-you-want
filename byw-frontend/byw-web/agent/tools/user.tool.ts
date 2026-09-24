/**
 * 用户域工具：收货地址、收藏、关注店铺（全部只读）。
 */
import type { ToolDefinition } from '~/agent/sdk/index.js'
import { get } from '~/utils/request'
import { numOr } from '~/agent/tool-utils'

export default [
  {
    name: 'list_my_addresses',
    description: '查询当前用户的收货地址列表（含默认地址标记），下单时用其中的 id 作为 addressId',
    riskLevel: 'read',
    parameters: { type: 'object', properties: {} },
    handler: async () => {
      const list = await get<any[]>('/user/address/list')
      return {
        count: list?.length ?? 0,
        addresses: (list || []).map((a: any) => ({
          id: a.id,
          receiver: a.receiver,
          phone: a.phone,
          region: [a.province, a.city, a.district].filter(Boolean).join(' '),
          detail: a.detail,
          isDefault: a.isDefault,
        })),
      }
    },
  },

  {
    name: 'list_my_favorites',
    description: '查询当前用户收藏的商品列表',
    riskLevel: 'read',
    parameters: {
      type: 'object',
      properties: {
        pageNum: { type: 'number', description: '页码，默认 1' },
        pageSize: { type: 'number', description: '每页条数，默认 10' },
      },
    },
    handler: async (params) => {
      const data = await get<any>('/user/favorite/list', {
        pageNum: numOr(params, 'pageNum', 1),
        pageSize: numOr(params, 'pageSize', 10),
      })
      return {
        total: data?.total ?? 0,
        list: (data?.list || []).map((f: any) => ({
          productId: f.productId,
          productName: f.productName,
          price: f.price,
        })),
      }
    },
  },

  {
    name: 'list_my_followed_shops',
    description: '查询当前用户关注的店铺列表',
    riskLevel: 'read',
    parameters: {
      type: 'object',
      properties: {
        pageNum: { type: 'number', description: '页码，默认 1' },
        pageSize: { type: 'number', description: '每页条数，默认 10' },
      },
    },
    handler: (params) =>
      get('/user/shop-follow/list', {
        pageNum: numOr(params, 'pageNum', 1),
        pageSize: numOr(params, 'pageSize', 10),
      }),
  },
] satisfies ToolDefinition[]

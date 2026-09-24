/**
 * 商品域工具：搜索、详情、评价查询（全部只读）。
 */
import type { ToolDefinition } from '~/agent/sdk/index.js'
import { get } from '~/utils/request'
import { num, numOr, optionalNum, optionalStr } from '~/agent/tool-utils'

export default [
  {
    name: 'search_products',
    description: '按关键词、分类、价格区间搜索商品，可按销量或上新时间排序，返回商品列表',
    riskLevel: 'read',
    parameters: {
      type: 'object',
      properties: {
        keyword: { type: 'string', description: '搜索关键词，匹配商品名称' },
        category: { type: 'string', description: '商品分类名称' },
        sort: { type: 'string', description: '排序方式', enum: ['sales', 'new', 'price_asc', 'price_desc'] },
        minPrice: { type: 'number', description: '最低价格' },
        maxPrice: { type: 'number', description: '最高价格' },
        pageNum: { type: 'number', description: '页码，默认 1' },
        pageSize: { type: 'number', description: '每页条数，默认 10' },
      },
    },
    handler: async (params) => {
      const data = await get<any>('/product/list', {
        pageNum: numOr(params, 'pageNum', 1),
        pageSize: numOr(params, 'pageSize', 10),
        keyword: optionalStr(params, 'keyword'),
        category: optionalStr(params, 'category'),
        sort: optionalStr(params, 'sort'),
        minPrice: optionalNum(params, 'minPrice'),
        maxPrice: optionalNum(params, 'maxPrice'),
      })
      return {
        total: data?.total ?? 0,
        list: (data?.list || []).map((p: any) => ({
          id: p.id,
          name: p.name,
          minPrice: p.minPrice,
          salesCount: p.salesCount,
          shopId: p.shopId,
        })),
      }
    },
  },

  {
    name: 'get_product_detail',
    description: '根据商品 ID 获取商品详情（价格、库存、规格、店铺、描述）',
    riskLevel: 'read',
    parameters: {
      type: 'object',
      properties: { productId: { type: 'number', description: '商品 ID' } },
      required: ['productId'],
    },
    handler: (params) => get(`/product/${num(params, 'productId')}`),
  },

  {
    name: 'get_product_reviews',
    description: '获取指定商品的评价列表与评分统计',
    riskLevel: 'read',
    parameters: {
      type: 'object',
      properties: {
        productId: { type: 'number', description: '商品 ID' },
        pageNum: { type: 'number', description: '页码，默认 1' },
        pageSize: { type: 'number', description: '每页条数，默认 10' },
      },
      required: ['productId'],
    },
    handler: async (params) => {
      const productId = num(params, 'productId')
      const [list, stats] = await Promise.all([
        get<any>(`/review/product/${productId}`, {
          pageNum: numOr(params, 'pageNum', 1),
          pageSize: numOr(params, 'pageSize', 10),
        }),
        get<any>(`/review/stats/${productId}`).catch(() => null),
      ])
      return {
        avgRating: stats?.avgRating ?? null,
        totalCount: stats?.totalCount ?? 0,
        reviews: (list?.list || []).map((r: any) => ({
          username: r.username,
          rating: r.rating,
          content: r.content,
          date: r.date,
        })),
      }
    },
  },
] satisfies ToolDefinition[]

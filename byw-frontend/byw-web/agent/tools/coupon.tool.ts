/**
 * 优惠券域工具：领券中心、我的券、领券。
 */
import type { ToolDefinition } from '~/agent/sdk/index.js'
import { get, post } from '~/utils/request'
import { num, numOr, optionalNum } from '~/agent/tool-utils'

export default [
  {
    name: 'list_available_coupons',
    description: '查询领券中心可领取的优惠券列表（含门槛、面额、有效期）',
    riskLevel: 'read',
    parameters: {
      type: 'object',
      properties: { newUser: { type: 'boolean', description: '是否只看新人专享券，默认 false' } },
    },
    handler: (params) =>
      get('/coupon/list', { newUser: params.newUser ? 1 : 0 }),
  },

  {
    name: 'list_my_coupons',
    description: '查询当前用户已领取的优惠券，可按状态筛选（0=未使用，1=已使用，2=已过期）',
    riskLevel: 'read',
    parameters: {
      type: 'object',
      properties: {
        status: { type: 'number', description: '券状态：0=未使用，1=已使用，2=已过期；不传表示全部' },
        pageNum: { type: 'number', description: '页码，默认 1' },
        pageSize: { type: 'number', description: '每页条数，默认 10' },
      },
    },
    handler: (params) =>
      get('/coupon/my-coupons', {
        status: optionalNum(params, 'status'),
        pageNum: numOr(params, 'pageNum', 1),
        pageSize: numOr(params, 'pageSize', 10),
      }),
  },

  {
    name: 'claim_coupon',
    description: '领取指定优惠券（需要 couponId，可从领券中心列表获得）',
    riskLevel: 'write',
    parameters: {
      type: 'object',
      properties: { couponId: { type: 'number', description: '优惠券 ID' } },
      required: ['couponId'],
    },
    handler: async (params) => {
      const couponId = num(params, 'couponId')
      await post(`/coupon/claim/${couponId}`)
      return { ok: true, couponId, action: 'claimed' }
    },
  },
] satisfies ToolDefinition[]

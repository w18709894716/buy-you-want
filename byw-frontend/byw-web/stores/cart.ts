import { defineStore } from 'pinia'

interface CartItem {
  cartId: number
  skuId: number
  productId: number
  productName: string
  image: string
  price: number
  quantity: number
  specData: string
  checked: boolean
}

interface CartState {
  items: CartItem[]
}

export const useCartStore = defineStore('cart', {
  state: (): CartState => ({
    items: [],
  }),

  getters: {
    /** 总商品数量 */
    totalCount: (state): number => {
      return state.items.reduce((sum, item) => sum + item.quantity, 0)
    },

    /** 已勾选商品数量 */
    checkedCount: (state): number => {
      return state.items.filter(item => item.checked).reduce((sum, item) => sum + item.quantity, 0)
    },

    /** 已勾选商品总价 */
    totalPrice: (state): number => {
      return state.items
        .filter(item => item.checked)
        .reduce((sum, item) => sum + item.price * item.quantity, 0)
    },

    /** 是否全选 */
    isAllChecked: (state): boolean => {
      return state.items.length > 0 && state.items.every(item => item.checked)
    },

    /** 已勾选的商品列表 */
    checkedItems: (state): CartItem[] => {
      return state.items.filter(item => item.checked)
    },
  },

  actions: {
    /** 获取购物车列表 */
    async getCartList() {
      try {
        const data = await get<any[]>('/cart/list')
        this.items = data.map(item => ({
          cartId: item.id,
          skuId: item.skuId,
          productId: item.productId,
          productName: item.productName || item.skuName,
          image: item.productImage,
          price: item.price,
          quantity: item.quantity,
          specData: item.specData || '',
          checked: item.selected === 1,
        }))
      } catch {
        // 未登录时使用本地缓存
        const cached = localStorage.getItem('byw_cart')
        if (cached) {
          this.items = JSON.parse(cached)
        }
      }
    },

    /** 添加商品到购物车 */
    async addToCart(skuId: number, quantity: number) {
      await post('/cart/add', null, { params: { skuId, quantity } })
      await this.getCartList()
    },

    /** 删除购物车商品 */
    async removeItem(cartId: number) {
      const item = this.items.find(i => i.cartId === cartId)
      if (!item) return
      try {
        await del(`/cart/${item.skuId}`)
      } catch {
        // ignore
      }
      this.items = this.items.filter(i => i.cartId !== cartId)
      this.saveLocal()
    },

    /** 更新商品数量 */
    async updateQuantity(cartId: number, quantity: number) {
      const item = this.items.find(i => i.cartId === cartId)
      if (!item) return

      if (quantity <= 0) {
        await this.removeItem(cartId)
        return
      }

      try {
        await put('/cart/update', null, { params: { skuId: item.skuId, quantity } })
      } catch {
        // ignore
      }
      item.quantity = quantity
      this.saveLocal()
    },

    /** 全选/取消全选 */
    toggleAll() {
      const newState = !this.isAllChecked
      this.items.forEach(item => (item.checked = newState))
    },

    /** 切换单个商品选中状态 */
    toggleItem(cartId: number) {
      const item = this.items.find(item => item.cartId === cartId)
      if (item) {
        item.checked = !item.checked
      }
    },

    /** 购物车内切换规格 */
    async changeSku(cartId: number, newSkuId: number) {
      const item = this.items.find(i => i.cartId === cartId)
      if (!item) return
      await put('/cart/change-sku', null, { params: { oldSkuId: item.skuId, newSkuId } })
      await this.getCartList()
    },

    /** 保存到本地缓存 */
    saveLocal() {
      if (import.meta.client) {
        localStorage.setItem('byw_cart', JSON.stringify(this.items))
      }
    },
  },
})

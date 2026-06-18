import { defineStore } from 'pinia'

interface CartItem {
  cartId: number
  productId: number
  productName: string
  image: string
  price: number
  quantity: number
  skuSpecs: string
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
        const data = await get<CartItem[]>('/cart/list')
        this.items = data.map(item => ({ ...item, checked: true }))
      } catch {
        // 未登录时使用本地缓存
        const cached = localStorage.getItem('byw_cart')
        if (cached) {
          this.items = JSON.parse(cached)
        }
      }
    },

    /** 添加商品到购物车 */
    async addToCart(productId: number, quantity: number, skuSpecs: string = '') {
      try {
        await post('/cart/add', { productId, quantity, skuSpecs })
        await this.getCartList()
      } catch {
        // 未登录时存本地
        const existing = this.items.find(item => item.productId === productId && item.skuSpecs === skuSpecs)
        if (existing) {
          existing.quantity += quantity
        } else {
          this.items.push({
            cartId: Date.now(),
            productId,
            productName: '商品',
            image: '',
            price: 0,
            quantity,
            skuSpecs,
            checked: true,
          })
        }
        this.saveLocal()
      }
    },

    /** 删除购物车商品 */
    async removeItem(cartId: number) {
      try {
        await del(`/cart/${cartId}`)
      } catch {
        // ignore
      }
      this.items = this.items.filter(item => item.cartId !== cartId)
      this.saveLocal()
    },

    /** 更新商品数量 */
    async updateQuantity(cartId: number, quantity: number) {
      const item = this.items.find(item => item.cartId === cartId)
      if (!item) return

      if (quantity <= 0) {
        await this.removeItem(cartId)
        return
      }

      try {
        await put(`/cart/${cartId}`, { quantity })
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

    /** 保存到本地缓存 */
    saveLocal() {
      if (import.meta.client) {
        localStorage.setItem('byw_cart', JSON.stringify(this.items))
      }
    },
  },
})

import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器：添加 Token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('admin_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：解包 R<T> 结构，处理 401
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 如果响应是 R<T> 结构 { code, message, data }
    if (res.code !== undefined) {
      if (res.code === 200 || res.code === 0) {
        return res.data
      }
      // 401 未授权
      if (res.code === 401) {
        ElMessage.error('登录已过期，请重新登录')
        localStorage.removeItem('admin_token')
        router.push('/login')
        return Promise.reject(new Error(res.message || '未授权'))
      }
      // 其他错误
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('admin_token')
      router.push('/login')
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request

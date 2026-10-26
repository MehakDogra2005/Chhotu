async function request(method, url, body, token) {
  const headers = { ...(body && { 'Content-Type': 'application/json' }), ...(token && { Authorization: `Bearer ${token}` }) }
  const res = await fetch(url, { method, headers, body: body && JSON.stringify(body) }).catch(() => {
    throw new Error('Cannot reach Dukaan Saathi. Please check your internet connection.')
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw Object.assign(new Error(data.message || 'Something went wrong'), { status: res.status })
  return data
}

export const post = (url, body, token) => request('POST', url, body, token)
export const put = (url, body, token) => request('PUT', url, body, token)
export const del = (url, token) => request('DELETE', url, undefined, token)
export const get = (url, token) => request('GET', url, undefined, token)

import { queryClient } from '@/lib/queryClient'

describe('queryClient', () => {
  it('uses stable dashboard query defaults', () => {
    expect(queryClient.getDefaultOptions().queries).toMatchObject({
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 5_000,
    })
  })
})

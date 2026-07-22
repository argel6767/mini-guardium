import { cn } from '@/lib/utils'

describe('cn', () => {
  it('combines conditional classes and keeps the latest Tailwind utility', () => {
    expect(cn('rounded', { hidden: false }, ['px-2', { block: true }], 'px-4'))
      .toBe('rounded block px-4')
  })
})


import { render, screen } from '@testing-library/react'

import { SeverityMixPanel } from '@/components/dashboard/SeverityMixPanel'
import { severityRows } from '@/components/dashboard/severityRows'

describe('SeverityMixPanel', () => {
  it('renders every configured severity row', () => {
    render(<SeverityMixPanel />)

    expect(screen.getByRole('heading', { name: 'Severity mix' })).toBeInTheDocument()

    for (const row of severityRows) {
      expect(screen.getByText(row.label)).toBeInTheDocument()
      expect(screen.getByLabelText(`${row.label} severity placeholder`)).toBeInTheDocument()
    }
  })
})


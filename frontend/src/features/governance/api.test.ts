import { describe, expect, it, vi } from 'vitest'

import { saveBatchResults } from './api'
import type { BatchResultCommand } from './types'

describe('governance api', () => {
  it('keeps per-item batch outcomes', async () => {
    const commands: BatchResultCommand[] = [
      {
        itemId: 501,
        itemVersion: 0,
        assetVersion: 7,
        proposedValue: { description: '标准说明' },
        submit: true,
      },
      {
        itemId: 502,
        itemVersion: 0,
        assetVersion: 8,
        proposedValue: { description: '另一说明' },
        submit: true,
      },
    ]
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      results: [
        { itemId: 501, outcome: 'SUCCESS', resultVersionId: 9001 },
        {
          itemId: 502,
          outcome: 'CONFLICT',
          errorCode: 'governance_version_conflict',
          currentVersion: 4,
        },
      ],
    }), { status: 200 }))

    await expect(saveBatchResults('batch-1', commands)).resolves.toMatchObject({
      results: [{ outcome: 'SUCCESS' }, { outcome: 'CONFLICT', currentVersion: 4 }],
    })
  })
})

import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import {
  DEFAULT_RECRUITMENT_STATUS,
  RECRUITMENT_STATUS_LABELS,
  RECRUITMENT_STATUS_OPTIONS,
  normalizeRecruitmentStatus,
  recruitmentStatusLabel
} from './recruitmentStatus.js'

describe('recruitmentStatus', () => {
  it('should expose the unified Chinese labels', () => {
    assert.deepEqual(RECRUITMENT_STATUS_LABELS, {
      TO_CONTACT: '待联系',
      TO_INTERVIEW: '待面试',
      REJECTED: '已拒绝'
    })
  })

  it('should keep the supported option list in enum order', () => {
    assert.deepEqual(RECRUITMENT_STATUS_OPTIONS, [
      { value: 'TO_CONTACT', label: '待联系' },
      { value: 'TO_INTERVIEW', label: '待面试' },
      { value: 'REJECTED', label: '已拒绝' }
    ])
  })

  it('should fall back to the default enum for unknown values', () => {
    assert.equal(normalizeRecruitmentStatus('UNKNOWN'), DEFAULT_RECRUITMENT_STATUS)
    assert.equal(recruitmentStatusLabel('UNKNOWN'), '待联系')
  })
})

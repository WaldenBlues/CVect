import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import {
  buildSemanticRankMaps,
  buildSemanticSearchPayload,
  DEFAULT_SEMANTIC_MAPPING,
  reconcileSemanticRankMaps,
  suggestSemanticWeights
} from './semanticMatching.js'

describe('semanticMatching', () => {
  it('buildSemanticSearchPayload should use default Experience/Skill mapping', () => {
    const payload = buildSemanticSearchPayload('  Java backend JD  ', { topK: 20 })
    assert.deepEqual(payload, {
      jobDescription: 'Java backend JD',
      topK: 20,
      filterByExperience: DEFAULT_SEMANTIC_MAPPING.filterByExperience,
      filterBySkill: DEFAULT_SEMANTIC_MAPPING.filterBySkill,
      experienceWeight: 0.5,
      skillWeight: 0.5,
      onlyVectorReadyCandidates: true
    })
  })

  it('buildSemanticSearchPayload should clamp invalid topK', () => {
    const payload = buildSemanticSearchPayload('JD', { topK: -5 })
    assert.equal(payload.topK, 100)
  })

  it('buildSemanticSearchPayload should reject fractional topK below one', () => {
    const payload = buildSemanticSearchPayload('JD', { topK: 0.5 })
    assert.equal(payload.topK, 100)
  })

  it('buildSemanticSearchPayload should carry provided tuning weights', () => {
    const payload = buildSemanticSearchPayload('JD', { topK: 10, experienceWeight: 0.2, skillWeight: 0.8 })
    assert.equal(payload.experienceWeight, 0.2)
    assert.equal(payload.skillWeight, 0.8)
  })

  it('buildSemanticSearchPayload should clamp tuning weights into range', () => {
    const payload = buildSemanticSearchPayload('JD', { experienceWeight: 1.4, skillWeight: -0.3 })
    assert.equal(payload.experienceWeight, 1)
    assert.equal(payload.skillWeight, 0)
  })

  it('buildSemanticRankMaps should build score and rank maps from search response', () => {
    const resp = {
      candidates: [
        { candidateId: 'c1', score: 0.82 },
        { candidateId: 'c2', score: 0.76 },
        { candidateId: 'c1', score: 0.9 }, // duplicate id uses max score
        { candidateId: 'c3', score: 'bad' } // invalid score ignored
      ]
    }
    const result = buildSemanticRankMaps(resp)

    assert.deepEqual(result.scoreByCandidateId, {
      c1: 0.9,
      c2: 0.76
    })
    assert.deepEqual(result.rankByCandidateId, {
      c1: 2,
      c2: 1
    })
  })

  it('buildSemanticRankMaps should ignore nullish or blank scores', () => {
    const resp = {
      candidates: [
        { candidateId: 'c1', score: null },
        { candidateId: 'c2', score: '   ' },
        { candidateId: 'c3', score: 0 },
        { candidateId: 'c4', score: '0.35' }
      ]
    }
    const result = buildSemanticRankMaps(resp)

    assert.deepEqual(result.scoreByCandidateId, {
      c3: 0,
      c4: 0.35
    })
    assert.deepEqual(result.rankByCandidateId, {
      c3: 2,
      c4: 3
    })
  })

  it('reconcileSemanticRankMaps should keep ready database candidates visible without fabricating zero scores', () => {
    const result = reconcileSemanticRankMaps({
      searchResponse: {
        candidates: [
          { candidateId: 'c1', score: 0.82 },
          { candidateId: 'other-jd', score: 0.91 }
        ]
      },
      candidateEvents: [
        { id: 'c1', vectorStatus: 'READY' },
        { id: 'c2', vectorStatus: 'READY' },
        { id: 'c3', vectorStatus: 'PROCESSING' }
      ]
    })

    assert.equal(result.matchedCount, 1)
    assert.deepEqual(result.scoreByCandidateId, {
      c1: 0.82
    })
    assert.deepEqual(result.rankByCandidateId, {
      c1: 0,
      c2: 1
    })
  })

  it('reconcileSemanticRankMaps should preserve existing semantic scores during db-only refresh', () => {
    const result = reconcileSemanticRankMaps({
      searchResponse: {
        candidates: []
      },
      candidateEvents: [
        { id: 'c1', vectorStatus: 'READY' },
        { id: 'c2', vectorStatus: 'READY' }
      ],
      previousScoreByCandidateId: {
        c1: 0.67
      },
      previousRankByCandidateId: {
        c1: 0
      }
    })

    assert.equal(result.matchedCount, 0)
    assert.deepEqual(result.scoreByCandidateId, {
      c1: 0.67
    })
    assert.deepEqual(result.rankByCandidateId, {
      c1: 0,
      c2: 1
    })
  })

  it('suggestSemanticWeights should adapt to JD keywords', () => {
    const skillHeavy = suggestSemanticWeights('Java Spring Kubernetes Redis skill stack')
    const expHeavy = suggestSemanticWeights('10年架构经验，负责主导交付与团队协作')
    assert.ok(skillHeavy.skillWeight > skillHeavy.experienceWeight)
    assert.ok(expHeavy.experienceWeight > expHeavy.skillWeight)
  })
})

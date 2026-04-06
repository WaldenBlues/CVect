const DEFAULT_TOP_K = 100

const clampTopK = (topK) => {
  if (!Number.isFinite(topK) || topK <= 0) return DEFAULT_TOP_K
  const normalized = Math.floor(topK)
  if (normalized <= 0) return DEFAULT_TOP_K
  return Math.min(normalized, 500)
}

const clampWeight = (weight) => {
  if (!Number.isFinite(weight)) return 0.5
  return Math.min(Math.max(0, weight), 1)
}

export const DEFAULT_SEMANTIC_MAPPING = Object.freeze({
  filterByExperience: true,
  filterBySkill: true
})

export const buildSemanticSearchPayload = (jobDescription, options = {}) => {
  const text = typeof jobDescription === 'string' ? jobDescription.trim() : ''
  const experienceWeight = clampWeight(options.experienceWeight)
  const skillWeight = clampWeight(options.skillWeight)
  return {
    jobDescription: text,
    topK: clampTopK(options.topK ?? DEFAULT_TOP_K),
    filterByExperience: DEFAULT_SEMANTIC_MAPPING.filterByExperience,
    filterBySkill: DEFAULT_SEMANTIC_MAPPING.filterBySkill,
    experienceWeight,
    skillWeight,
    onlyVectorReadyCandidates: true
  }
}

const parseCandidateScore = (value) => {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return null
    const parsed = Number(trimmed)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

export const buildSemanticRankMaps = (searchResponse) => {
  const candidates = Array.isArray(searchResponse?.candidates) ? searchResponse.candidates : []
  const scoreByCandidateId = {}
  const rankByCandidateId = {}

  candidates.forEach((candidate, index) => {
    const candidateId = candidate?.candidateId
    if (!candidateId) return

    const score = parseCandidateScore(candidate?.score)
    if (score == null) return

    if (!(candidateId in scoreByCandidateId) || score > scoreByCandidateId[candidateId]) {
      scoreByCandidateId[candidateId] = score
      rankByCandidateId[candidateId] = index
    }
  })

  return { scoreByCandidateId, rankByCandidateId }
}

export const reconcileSemanticRankMaps = ({
  searchResponse,
  candidateEvents,
  previousScoreByCandidateId,
  previousRankByCandidateId,
  storedScoreByCandidateId,
  storedRankByCandidateId
} = {}) => {
  const currentCandidates = Array.isArray(candidateEvents) ? candidateEvents : []
  const currentCandidateIds = new Set(
    currentCandidates
      .map((item) => item?.id)
      .filter(Boolean)
  )
  const previousScores = previousScoreByCandidateId && typeof previousScoreByCandidateId === 'object'
    ? previousScoreByCandidateId
    : {}
  const previousRanks = previousRankByCandidateId && typeof previousRankByCandidateId === 'object'
    ? previousRankByCandidateId
    : {}
  const storedScores = storedScoreByCandidateId && typeof storedScoreByCandidateId === 'object'
    ? storedScoreByCandidateId
    : {}
  const storedRanks = storedRankByCandidateId && typeof storedRankByCandidateId === 'object'
    ? storedRankByCandidateId
    : {}
  const searchCandidates = Array.isArray(searchResponse?.candidates)
    ? searchResponse.candidates.filter((candidate) => {
      const candidateId = candidate?.candidateId
      return candidateId && (!currentCandidateIds.size || currentCandidateIds.has(candidateId))
    })
    : []
  const { scoreByCandidateId, rankByCandidateId } = buildSemanticRankMaps({ candidates: searchCandidates })
  const matchedCount = Object.keys(scoreByCandidateId).length

  for (const candidateId of currentCandidateIds) {
    if (Object.prototype.hasOwnProperty.call(scoreByCandidateId, candidateId)) continue
    const previousScore = parseCandidateScore(previousScores[candidateId])
    if (previousScore == null) continue
    scoreByCandidateId[candidateId] = previousScore
    const previousRank = previousRanks[candidateId]
    if (typeof previousRank === 'number' && Number.isFinite(previousRank)) {
      rankByCandidateId[candidateId] = previousRank
    }
  }

  for (const candidateId of currentCandidateIds) {
    if (Object.prototype.hasOwnProperty.call(scoreByCandidateId, candidateId)) continue
    const storedScore = parseCandidateScore(storedScores[candidateId])
    if (storedScore == null) continue
    scoreByCandidateId[candidateId] = storedScore
    const storedRank = storedRanks[candidateId]
    if (typeof storedRank === 'number' && Number.isFinite(storedRank)) {
      rankByCandidateId[candidateId] = storedRank
    }
  }

  const rankValues = Object.values(rankByCandidateId).filter((value) => typeof value === 'number' && Number.isFinite(value))
  let nextRank = rankValues.length ? Math.max(...rankValues) + 1 : 0

  for (const item of currentCandidates) {
    if (item?.vectorStatus !== 'READY' || !item.id) continue
    if (Object.prototype.hasOwnProperty.call(scoreByCandidateId, item.id)) {
      if (!Object.prototype.hasOwnProperty.call(rankByCandidateId, item.id)) {
        rankByCandidateId[item.id] = nextRank
        nextRank += 1
      }
      continue
    }
    if (Object.prototype.hasOwnProperty.call(rankByCandidateId, item.id)) continue
    rankByCandidateId[item.id] = nextRank
    nextRank += 1
  }

  return { scoreByCandidateId, rankByCandidateId, matchedCount }
}

const EXPERIENCE_HINTS = [
  '经验', '年', 'years', 'ownership', 'architecture', '架构', 'lead', '主导', '负责人', '交付'
]

const SKILL_HINTS = [
  '技能', 'skill', 'stack', 'java', 'python', 'golang', 'spring', 'react', 'k8s', 'kubernetes', 'mysql', 'redis'
]

export const suggestSemanticWeights = (jobDescription) => {
  const text = String(jobDescription || '').toLowerCase()
  const experienceHits = EXPERIENCE_HINTS.reduce((acc, kw) => acc + (text.includes(kw) ? 1 : 0), 0)
  const skillHits = SKILL_HINTS.reduce((acc, kw) => acc + (text.includes(kw) ? 1 : 0), 0)

  // 基础权重 + 关键词命中增益，最后归一化
  const experienceRaw = 1 + experienceHits
  const skillRaw = 1 + skillHits
  const sum = experienceRaw + skillRaw
  const experienceWeight = Number((experienceRaw / sum).toFixed(2))
  const skillWeight = Number((skillRaw / sum).toFixed(2))
  return { experienceWeight, skillWeight }
}

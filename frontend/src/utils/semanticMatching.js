const DEFAULT_TOP_K = 100

const clampTopK = (topK) => {
  if (!Number.isFinite(topK) || topK <= 0) return DEFAULT_TOP_K
  return Math.min(Math.floor(topK), 500)
}

export const DEFAULT_SEMANTIC_MAPPING = Object.freeze({
  filterByExperience: true,
  filterBySkill: true
})

export const buildSemanticSearchPayload = (jobDescription, options = {}) => {
  const text = typeof jobDescription === 'string' ? jobDescription.trim() : ''
  const experienceWeight = Number.isFinite(options.experienceWeight) ? Math.max(0, options.experienceWeight) : 0.5
  const skillWeight = Number.isFinite(options.skillWeight) ? Math.max(0, options.skillWeight) : 0.5
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

export const buildSemanticRankMaps = (searchResponse) => {
  const candidates = Array.isArray(searchResponse?.candidates) ? searchResponse.candidates : []
  const scoreByCandidateId = {}
  const rankByCandidateId = {}

  candidates.forEach((candidate, index) => {
    const candidateId = candidate?.candidateId
    if (!candidateId) return

    const score = Number(candidate?.score)
    if (!Number.isFinite(score)) return

    if (!(candidateId in scoreByCandidateId) || score > scoreByCandidateId[candidateId]) {
      scoreByCandidateId[candidateId] = score
      rankByCandidateId[candidateId] = index
    }
  })

  return { scoreByCandidateId, rankByCandidateId }
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

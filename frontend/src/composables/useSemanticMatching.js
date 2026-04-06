import { reactive, ref, watch } from 'vue'
import {
  buildSemanticSearchPayload,
  normalizeSemanticWeights,
  reconcileSemanticRankMaps,
  suggestSemanticWeights
} from '../utils/semanticMatching'

export const useSemanticMatching = ({ events, selectedJdId, selectedJd }) => {
  const semanticScoreMap = ref({})
  const semanticRankMap = ref({})
  const semanticRawCandidates = ref([])
  const semanticLoading = ref(false)
  const semanticMessage = ref('')
  const semanticTuning = reactive({
    experienceWeight: 0.5,
    skillWeight: 0.5
  })
  const semanticAutoTune = ref(true)

  let semanticRefreshTimer = null
  let semanticRequestSeq = 0

  const normalizeSemanticScore = (value) => {
    if (typeof value !== 'number' || Number.isNaN(value)) return null
    if (value < 0) return 0
    if (value > 1) return 1
    return value
  }

  const calibrateSemanticDisplayScore = (value) => {
    const raw = normalizeSemanticScore(value)
    if (raw == null) return null
    // 展示分采用温和映射：排序仍用 raw，UI 仅做可读性校准。
    if (raw <= 0.3) return 0
    const t = (raw - 0.3) / 0.7
    return Math.pow(Math.max(0, Math.min(1, t)), 1.05)
  }

  const semanticDisplayScore = (candidateId) => {
    return calibrateSemanticDisplayScore(semanticScoreMap.value[candidateId])
  }

  const hasPendingSemanticScore = (candidateId) => {
    const score = normalizeSemanticScore(semanticScoreMap.value[candidateId])
    if (score != null) return false
    const rank = semanticRankMap.value[candidateId]
    return typeof rank === 'number' && Number.isFinite(rank)
  }

  const semanticScorePercent = (candidateId) => {
    const score = semanticDisplayScore(candidateId)
    if (score == null) return hasPendingSemanticScore(candidateId) ? '待重算' : '--'
    return `${(score * 100).toFixed(1)}%`
  }

  const semanticScoreRaw = (candidateId) => {
    const score = normalizeSemanticScore(semanticScoreMap.value[candidateId])
    if (score == null) return hasPendingSemanticScore(candidateId) ? '(db)' : '(n/a)'
    return `(${score.toFixed(3)})`
  }

  const matchScoreClass = (candidateId) => {
    const score = semanticDisplayScore(candidateId)
    if (score == null) return hasPendingSemanticScore(candidateId) ? ['pending'] : ['empty']
    if (score >= 0.7) return ['level-high']
    if (score >= 0.35) return ['level-medium']
    return ['level-low']
  }

  const applySemanticTuningFromJd = () => {
    if (!semanticAutoTune.value) return
    const suggested = suggestSemanticWeights(selectedJd.value?.content || '')
    semanticTuning.experienceWeight = suggested.experienceWeight
    semanticTuning.skillWeight = suggested.skillWeight
  }

  const stopSemanticRefreshTimer = () => {
    if (semanticRefreshTimer) {
      clearTimeout(semanticRefreshTimer)
      semanticRefreshTimer = null
    }
  }

  const scheduleSemanticRefresh = (delayMs = 280) => {
    stopSemanticRefreshTimer()
    semanticRefreshTimer = setTimeout(() => {
      semanticRefreshTimer = null
      refreshSemanticRanking()
    }, delayMs)
  }

  const currentSemanticWeights = () => normalizeSemanticWeights(
    semanticTuning.experienceWeight,
    semanticTuning.skillWeight
  )

  const resolvePersistedSemanticScore = (candidate) => {
    const overall = normalizeSemanticScore(candidate?.baselineMatchScore)
    const experience = normalizeSemanticScore(candidate?.baselineExperienceScore)
    const skill = normalizeSemanticScore(candidate?.baselineSkillScore)
    if (experience == null && skill == null) {
      return overall
    }
    const { experienceWeight, skillWeight } = currentSemanticWeights()
    const weighted = (experience ?? 0) * experienceWeight + (skill ?? 0) * skillWeight
    if (weighted > 0) {
      return normalizeSemanticScore(weighted)
    }
    return overall ?? normalizeSemanticScore(Math.max(experience ?? 0, skill ?? 0))
  }

  const buildPersistedSemanticMaps = () => {
    const scoredEntries = []
    const pendingIds = []
    for (const item of events) {
      const score = resolvePersistedSemanticScore(item)
      if (score != null) {
        scoredEntries.push({ candidateId: item.id, score })
        continue
      }
      if (item?.vectorStatus === 'READY' && item?.id) {
        pendingIds.push(item.id)
      }
    }
    scoredEntries.sort((left, right) => right.score - left.score)
    const scoreByCandidateId = {}
    const rankByCandidateId = {}
    scoredEntries.forEach((entry, index) => {
      scoreByCandidateId[entry.candidateId] = entry.score
      rankByCandidateId[entry.candidateId] = index
    })
    let nextRank = scoredEntries.length
    for (const candidateId of pendingIds) {
      if (Object.prototype.hasOwnProperty.call(rankByCandidateId, candidateId)) continue
      rankByCandidateId[candidateId] = nextRank
      nextRank += 1
    }
    return {
      scoreByCandidateId,
      rankByCandidateId,
      matchedCount: scoredEntries.length
    }
  }

  const applySemanticRankingFromRaw = ({ preserveExistingScores = false } = {}) => {
    const candidates = Array.isArray(semanticRawCandidates.value) ? semanticRawCandidates.value : []
    const persistedMaps = buildPersistedSemanticMaps()
    const { scoreByCandidateId, rankByCandidateId, matchedCount } = reconcileSemanticRankMaps({
      searchResponse: { candidates },
      candidateEvents: events,
      previousScoreByCandidateId: preserveExistingScores ? semanticScoreMap.value : null,
      previousRankByCandidateId: preserveExistingScores ? semanticRankMap.value : null,
      storedScoreByCandidateId: persistedMaps.scoreByCandidateId,
      storedRankByCandidateId: persistedMaps.rankByCandidateId
    })
    semanticScoreMap.value = scoreByCandidateId
    semanticRankMap.value = rankByCandidateId
    return Math.max(matchedCount, persistedMaps.matchedCount)
  }

  const reconcileSemanticRanking = () => {
    return applySemanticRankingFromRaw({ preserveExistingScores: true })
  }

  const resetSemanticState = (clearMessage = true) => {
    semanticRequestSeq += 1
    semanticRawCandidates.value = []
    semanticScoreMap.value = {}
    semanticRankMap.value = {}
    if (clearMessage) {
      semanticMessage.value = ''
    }
    semanticLoading.value = false
  }

  const refreshSemanticRanking = async () => {
    const requestSeq = ++semanticRequestSeq
    if (!selectedJdId.value) {
      resetSemanticState(true)
      return
    }
    const jdContent = selectedJd.value?.content?.trim()
    if (!jdContent) {
      resetSemanticState(false)
      semanticMessage.value = '当前 JD 无文本内容，无法计算语义匹配。'
      return
    }
    semanticLoading.value = true
    semanticMessage.value = ''
    try {
      const { experienceWeight, skillWeight } = currentSemanticWeights()
      const payload = buildSemanticSearchPayload(jdContent, {
        topK: Math.max(events.length, 50),
        experienceWeight,
        skillWeight
      })
      const resp = await fetch('/api/search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      })
      if (!resp.ok) {
        let detail = ''
        try {
          const errData = await resp.json()
          detail = errData?.message || errData?.error || ''
        } catch (_) {
          detail = ''
        }
        throw new Error(detail ? `语义匹配计算失败: ${detail}` : '语义匹配计算失败')
      }
      const data = await resp.json()
      if (requestSeq !== semanticRequestSeq) {
        return
      }
      semanticRawCandidates.value = Array.isArray(data?.candidates) ? data.candidates : []
      const matchedCount = applySemanticRankingFromRaw()
      semanticMessage.value = matchedCount > 0
        ? `已完成语义重排，命中 ${matchedCount} 位候选人。`
        : '语义检索完成，但暂无可用分数（请确认候选人已完成向量化）。'
    } catch (err) {
      if (requestSeq !== semanticRequestSeq) {
        return
      }
      const matchedCount = applySemanticRankingFromRaw({ preserveExistingScores: true })
      semanticMessage.value = matchedCount > 0
        ? `${err.message}，已按数据库回退展示已就绪候选人。`
        : err.message
    } finally {
      if (requestSeq === semanticRequestSeq) {
        semanticLoading.value = false
      }
    }
  }

  watch(
    () => [semanticTuning.experienceWeight, semanticTuning.skillWeight],
    () => {
      if (selectedJdId.value && selectedJd.value?.content) {
        scheduleSemanticRefresh()
      }
    }
  )

  watch(
    () => [semanticAutoTune.value, selectedJd.value?.content],
    () => {
      if (semanticAutoTune.value) applySemanticTuningFromJd()
      if (selectedJdId.value && selectedJd.value?.content) {
        scheduleSemanticRefresh()
      }
    }
  )

  return {
    semanticScoreMap,
    semanticRankMap,
    semanticRawCandidates,
    semanticLoading,
    semanticMessage,
    semanticTuning,
    semanticAutoTune,
    semanticScorePercent,
    semanticScoreRaw,
    matchScoreClass,
    applySemanticTuningFromJd,
    reconcileSemanticRanking,
    scheduleSemanticRefresh,
    refreshSemanticRanking,
    resetSemanticState,
    stopSemanticRefreshTimer
  }
}

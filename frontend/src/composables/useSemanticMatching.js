import { reactive, ref, watch } from 'vue'
import { buildSemanticSearchPayload, suggestSemanticWeights } from '../utils/semanticMatching'

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

  const semanticScorePercent = (candidateId) => {
    const score = semanticDisplayScore(candidateId)
    if (score == null) return '--'
    return `${(score * 100).toFixed(1)}%`
  }

  const semanticScoreRaw = (candidateId) => {
    const score = normalizeSemanticScore(semanticScoreMap.value[candidateId])
    if (score == null) return '(n/a)'
    return `(${score.toFixed(3)})`
  }

  const matchScoreClass = (candidateId) => {
    const score = semanticDisplayScore(candidateId)
    if (score == null) return ['empty']
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

  const applySemanticRankingFromRaw = () => {
    const candidates = Array.isArray(semanticRawCandidates.value) ? semanticRawCandidates.value : []
    if (!candidates.length) {
      semanticScoreMap.value = {}
      semanticRankMap.value = {}
      return
    }
    const ranked = candidates
      .map((candidate, index) => {
        const score = Number(candidate?.score)
        return {
          candidateId: candidate?.candidateId,
          score: Number.isFinite(score) ? score : 0,
          index
        }
      })
      .filter((item) => item.candidateId && Number.isFinite(item.score))

    const scoreByCandidateId = {}
    const rankByCandidateId = {}
    ranked.forEach((item, index) => {
      scoreByCandidateId[item.candidateId] = item.score
      rankByCandidateId[item.candidateId] = index
    })
    let nextRank = ranked.length
    // /api/search 可能未覆盖全部已向量化候选人，给遗漏项补 0 分，避免卡片长期灰色。
    for (const item of events) {
      if (item?.vectorStatus !== 'READY') continue
      if (Object.prototype.hasOwnProperty.call(scoreByCandidateId, item.id)) continue
      scoreByCandidateId[item.id] = 0
      rankByCandidateId[item.id] = nextRank
      nextRank += 1
    }
    semanticScoreMap.value = scoreByCandidateId
    semanticRankMap.value = rankByCandidateId
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
      const payload = buildSemanticSearchPayload(jdContent, {
        topK: Math.max(events.length, 50),
        experienceWeight: semanticTuning.experienceWeight,
        skillWeight: semanticTuning.skillWeight
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
      applySemanticRankingFromRaw()
      const matchedCount = Object.keys(semanticScoreMap.value).length
      semanticMessage.value = matchedCount > 0
        ? `已完成语义重排，命中 ${matchedCount} 位候选人。`
        : '语义检索完成，但暂无可用分数（请确认候选人已完成向量化）。'
    } catch (err) {
      if (requestSeq !== semanticRequestSeq) {
        return
      }
      semanticRawCandidates.value = []
      semanticScoreMap.value = {}
      semanticRankMap.value = {}
      semanticMessage.value = err.message
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
    scheduleSemanticRefresh,
    refreshSemanticRanking,
    resetSemanticState,
    stopSemanticRefreshTimer
  }
}

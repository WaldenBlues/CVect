<template>
  <main>
    <section class="header">
      <div>
        <h1 class="title">CVect 实时候选人入库</h1>
        <p class="subtitle">
          通过 SSE 订阅入库事件，实时展示最新候选人信息与解析状态。
        </p>
      </div>
      <div class="status-panel">
        <div class="status-row">
          <span>连接状态</span>
          <span class="badge" :class="isConnected ? 'live' : 'offline'">
            {{ isConnected ? 'Live' : 'Offline' }}
          </span>
        </div>
      </div>
    </section>

    <section class="jd-board">
      <div class="panel jd-list">
        <div class="panel-header">
          <h3>JD 列表</h3>
          <div class="jd-header-actions">
            <button class="secondary small" @click="refreshJds">刷新</button>
            <button class="small" @click="openCreateJd">新建</button>
          </div>
        </div>
        <div class="jd-items">
          <button
            v-for="jd in jds"
            :key="jd.id"
            class="jd-item"
            :class="{ active: jd.id === selectedJdId }"
            @click="selectJd(jd)"
          >
            <div class="jd-card">
              <div class="jd-title">
                <span>{{ jd.title }}</span>
                <div class="jd-actions">
                  <button class="ghost" @click.stop="startEditJd(jd)">编辑</button>
                  <button class="ghost danger" @click.stop="deleteJd(jd)">删除</button>
                </div>
              </div>
              <div class="jd-meta">
                <span>{{ jd.createdAt || '—' }}</span>
                <span class="count">{{ jd.candidateCount }} 人</span>
              </div>
              <p class="jd-snippet" v-if="jd.content">{{ jd.content }}</p>
              <div class="jd-edit" v-if="editingJdId === jd.id">
                <input v-model="editTitle" type="text" placeholder="JD 标题" />
                <textarea v-model="editContent" rows="3" placeholder="JD 文本"></textarea>
                <div class="jd-edit-actions">
                  <button class="small" @click.stop="saveEditJd(jd)" :disabled="jdSaving || !editTitle.trim()">
                    {{ jdSaving ? '保存中...' : '保存' }}
                  </button>
                  <button class="secondary small" @click.stop="cancelEditJd">取消</button>
                </div>
              </div>
            </div>
          </button>
          <div v-if="!jds.length" class="empty">暂无 JD，请先创建。</div>
        </div>
        <p class="muted" v-if="jdMessage">{{ jdMessage }}</p>
      </div>

      <div class="panel jd-detail-card" :class="{ placeholder: !selectedJd }">
        <div class="panel-header">
          <h3>JD 详情</h3>
          <span class="jd-detail-state">{{ selectedJd ? '已选择' : '待选择' }}</span>
        </div>
        <template v-if="selectedJd">
          <div class="jd-detail-title">{{ selectedJd.title }}</div>
          <div class="jd-detail-meta">
            <span>创建时间</span>
            <strong>{{ selectedJd.createdAt || '—' }}</strong>
          </div>
          <div class="jd-detail-meta">
            <span>候选人数</span>
            <strong>{{ selectedJd.candidateCount || 0 }}</strong>
          </div>
          <p class="jd-detail-content">{{ selectedJd.content || '暂无 JD 描述内容。' }}</p>
        </template>
        <template v-else>
          <div class="jd-detail-placeholder">
            <strong>未选择 JD</strong>
            <p>从左侧列表选择一个 JD，这里会展示完整详情与关键信息。</p>
          </div>
        </template>
      </div>

      <div class="modal-backdrop" v-if="showCreateJd" @click="closeCreateJd">
        <div class="modal" @click.stop>
          <div class="panel-header">
            <h3>新建 JD</h3>
            <button class="secondary small" @click="closeCreateJd">关闭</button>
          </div>
          <label class="field">
            <span>标题</span>
            <input v-model="jdTitle" type="text" placeholder="例如：后端 Java 工程师" />
          </label>
          <label class="field">
            <span>JD 文本</span>
            <textarea v-model="jdText" rows="6" placeholder="可选：岗位描述"></textarea>
          </label>
          <button class="small" @click="createJd" :disabled="jdSaving || !jdTitle.trim()">
            {{ jdSaving ? '创建中...' : '创建' }}
          </button>
        </div>
      </div>

      <div
        class="panel jd-upload dropzone-panel"
        :class="{ active: isDragActive, disabled: !selectedJdId }"
        @dragover.prevent="onDragOver"
        @dragleave.prevent="onDragLeave"
        @drop.prevent="onDrop"
        @click="openFilePicker"
      >
        <div class="panel-header">
          <h3>上传简历</h3>
        </div>
        <div class="upload-target">
          <span>当前 JD</span>
          <strong>{{ selectedJd?.title || '未选择' }}</strong>
        </div>
        <div class="dropzone">
          <div class="dropzone-inner">
            <strong>拖动简历到此处</strong>
            <span v-if="selectedJdId">支持 PDF / DOC / DOCX / TXT / MD</span>
            <span v-else>请先选择 JD</span>
          </div>
          <div class="dropzone-files" v-if="uploadFiles.length">
            {{ uploadFiles.map((f) => f.name).join(', ') }}
          </div>
        </div>
        <input ref="fileInputRef" type="file" multiple class="file-input" @change="onFileChange" />
        <p class="muted" v-if="uploadMessage">{{ uploadMessage }}</p>
      </div>
    </section>

    <section class="filters">
      <input
        v-model="filterText"
        class="filter-input"
        type="text"
        placeholder="按姓名 / 文件名 / 邮箱 / 电话 / 关键词搜索"
      />
      <label class="filter-select">
        <span>招聘状态</span>
        <select v-model="recruitmentFilter">
          <option value="">全部</option>
          <option v-for="option in recruitmentStatusOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <button v-if="hasActiveFilters" class="secondary small" @click="resetFilters">
        清空筛选
      </button>
      <span class="filter-stats">显示 {{ pageRangeText }} / {{ filteredCandidates.length }}（总计 {{ events.length }}）</span>
    </section>

    <SemanticPanel
      :loading="semanticLoading"
      :message="semanticMessage"
      v-model:autoTune="semanticAutoTune"
      v-model:experienceWeight="semanticTuning.experienceWeight"
      v-model:skillWeight="semanticTuning.skillWeight"
    />

    <section class="layout">
      <section class="cards">
        <article
          v-for="item in pagedCandidates"
          :key="item.id"
          class="card"
          :class="{ active: selectedCandidate?.id === item.id }"
          @click="selectCandidate(item)"
        >
          <div class="card-top">
            <h3>{{ item.title }}</h3>
            <div class="match-score" :class="matchScoreClass(item.id)">
              <span>匹配度</span>
              <strong>{{ semanticScorePercent(item.id) }}</strong>
              <small>{{ semanticScoreRaw(item.id) }}</small>
            </div>
          </div>
          <p>{{ item.summary }}</p>
          <div class="meta">
          <span>ID: {{ item.id }}</span>
          <span v-if="item.sourceFileName">文件: {{ item.sourceFileName }}</span>
            <span v-if="item.createdAt">时间: {{ item.createdAt }}</span>
          </div>
          <div class="tags">
            <span v-if="item.emails.length" class="tag">邮箱 {{ item.emails.length }}</span>
            <span v-if="item.links.length" class="tag">链接 {{ item.links.length }}</span>
            <span v-if="item.educations.length" class="tag">教育 {{ item.educations.length }}</span>
            <span v-if="item.honors.length" class="tag">荣誉 {{ item.honors.length }}</span>
            <span v-if="item.vectorStatus === 'READY'" class="tag">向量完成</span>
            <span v-else-if="item.vectorStatus === 'PROCESSING'" class="tag">向量处理中</span>
            <span v-else-if="item.vectorStatus === 'PARTIAL'" class="tag warning">向量部分完成</span>
            <span v-else-if="item.vectorStatus === 'FAILED'" class="tag danger">向量失败</span>
            <span v-else-if="item.noVectorChunk" class="tag warning">无向量分块</span>
          </div>
          <div class="recruitment-row">
            <span class="recruitment-badge" :class="`status-${item.recruitmentStatus || 'TO_CONTACT'}`">
              {{ recruitmentStatusLabel(item.recruitmentStatus) }}
            </span>
          </div>
        </article>
        <div v-if="!pagedCandidates.length" class="empty cards-empty">暂无候选人</div>
      </section>
      <section class="pager" v-if="filteredCandidates.length">
        <span class="pager-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
        <div class="pager-actions">
          <button class="secondary small" :disabled="currentPage <= 1" @click="goToPrevPage">
            上一页
          </button>
          <button class="secondary small" :disabled="currentPage >= totalPages" @click="goToNextPage">
            下一页
          </button>
        </div>
      </section>

      <aside class="detail" v-if="selectedCandidate">
        <div class="detail-header">
          <h2>候选人详情</h2>
          <span class="detail-id">{{ selectedCandidate.id }}</span>
        </div>
        <div class="detail-section">
          <h4>姓名</h4>
          <p>{{ selectedCandidate.name || '未识别' }}</p>
        </div>
        <div class="detail-section">
          <h4>来源</h4>
          <p>{{ selectedCandidate.sourceFileName || '未知文件' }}</p>
          <p class="muted">{{ selectedCandidate.contentType || '未知类型' }}</p>
        </div>
        <div class="detail-section">
          <h4>招聘进度</h4>
          <div class="recruitment-actions">
            <button
              v-for="option in recruitmentStatusOptions"
              :key="option.value"
              class="status-btn"
              :class="{ active: selectedCandidate.recruitmentStatus === option.value }"
              :disabled="recruitmentUpdatingId === selectedCandidate.id"
              @click="setRecruitmentStatus(selectedCandidate, option.value)"
            >
              {{ option.label }}
            </button>
          </div>
          <p class="muted" v-if="recruitmentMessage">{{ recruitmentMessage }}</p>
        </div>
        <div class="detail-section">
          <h4>联系方式</h4>
          <ul>
            <li v-for="email in selectedCandidate.emails" :key="email">{{ email }}</li>
            <li v-for="phone in selectedCandidate.phones" :key="phone">{{ phone }}</li>
            <li v-if="!selectedCandidate.emails.length && !selectedCandidate.phones.length" class="muted">
              暂无联系方式
            </li>
          </ul>
        </div>
        <div class="detail-section">
          <h4>教育</h4>
          <ul>
            <li v-for="item in selectedCandidate.educations" :key="item">{{ item }}</li>
            <li v-if="!selectedCandidate.educations.length" class="muted">暂无教育记录</li>
          </ul>
        </div>
        <div class="detail-section">
          <h4>荣誉</h4>
          <ul>
            <li v-for="item in selectedCandidate.honors" :key="item">{{ item }}</li>
            <li v-if="!selectedCandidate.honors.length" class="muted">暂无荣誉记录</li>
          </ul>
        </div>
        <div class="detail-section">
          <h4>链接</h4>
          <ul>
            <li v-for="item in selectedCandidate.links" :key="item">{{ item }}</li>
            <li v-if="!selectedCandidate.links.length" class="muted">暂无链接</li>
          </ul>
        </div>
      </aside>
    </section>

    <section class="log">
      <div v-for="entry in log" :key="entry.ts + entry.message" class="log-entry">
        [{{ entry.ts }}] {{ entry.message }}
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import SemanticPanel from './components/SemanticPanel.vue'
import { useSemanticMatching } from './composables/useSemanticMatching'

const sseUrl = import.meta.env.VITE_SSE_URL || '/api/candidates/stream'

const isConnected = ref(false)
const events = reactive([])
const log = reactive([])
const selectedCandidate = ref(null)
const isManualDisconnect = ref(false)
let reconnectTimer = null

const selectedJd = computed(() => {
  return jds.value.find((jd) => jd.id === selectedJdId.value) || null
})

const filterText = ref('')
const recruitmentFilter = ref('')

let source = null
let jdRefreshTimer = null
let vectorFlagPollTimer = null
let candidateLoadSeq = 0

const jds = ref([])
const selectedJdId = ref('')
const jdTitle = ref('')
const jdText = ref('')
const jdSaving = ref(false)
const jdMessage = ref('')
const showCreateJd = ref(false)
const uploadFiles = ref([])
const uploading = ref(false)
const uploadMessage = ref('')
const isDragActive = ref(false)
const fileInputRef = ref(null)
const editingJdId = ref('')
const editTitle = ref('')
const editContent = ref('')
const recruitmentUpdatingId = ref('')
const recruitmentMessage = ref('')
const PAGE_SIZE = 20
const currentPage = ref(1)

const {
  semanticScoreMap,
  semanticRankMap,
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
} = useSemanticMatching({ events, selectedJdId, selectedJd })

const recruitmentStatusOptions = [
  { value: 'TO_CONTACT', label: '待沟通' },
  { value: 'TO_INTERVIEW', label: '待面试' },
  { value: 'REJECTED', label: '淘汰' }
]

const recruitmentStatusLabelMap = {
  TO_CONTACT: '待沟通',
  TO_INTERVIEW: '待面试',
  REJECTED: '淘汰'
}

const recruitmentStatusLabel = (status) => {
  return recruitmentStatusLabelMap[status] || '待沟通'
}

const ensureStringArray = (value) => {
  if (Array.isArray(value)) {
    return value
      .map((item) => String(item ?? '').trim())
      .filter(Boolean)
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed ? [trimmed] : []
  }
  return []
}

const candidateSearchText = (item) => {
  return [
    item.id,
    item.name,
    item.title,
    item.sourceFileName,
    item.summary,
    item.contentType,
    item.emails.join(' '),
    item.phones.join(' '),
    item.educations.join(' '),
    item.honors.join(' '),
    item.links.join(' '),
    recruitmentStatusLabel(item.recruitmentStatus)
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
}

const hasActiveFilters = computed(() => {
  return Boolean(filterText.value.trim() || recruitmentFilter.value)
})

const resetFilters = () => {
  filterText.value = ''
  recruitmentFilter.value = ''
  currentPage.value = 1
}

const filteredCandidates = computed(() => {
  const keyword = filterText.value.trim().toLowerCase()
  const keywordParts = keyword ? keyword.split(/\s+/).filter(Boolean) : []
  const filtered = events.filter((item) => {
    if (selectedJdId.value && item.jdId && item.jdId !== selectedJdId.value) {
      return false
    }
    if (recruitmentFilter.value && item.recruitmentStatus !== recruitmentFilter.value) {
      return false
    }

    if (!keywordParts.length) return true
    const hay = candidateSearchText(item)
    return keywordParts.every((part) => hay.includes(part))
  })

  return filtered.sort((a, b) => {
    const aScore = semanticScoreMap.value[a.id]
    const bScore = semanticScoreMap.value[b.id]
    if (typeof aScore === 'number' && typeof bScore === 'number' && aScore !== bScore) {
      return bScore - aScore
    }
    if (typeof aScore === 'number' && typeof bScore !== 'number') return -1
    if (typeof aScore !== 'number' && typeof bScore === 'number') return 1

    const aRank = semanticRankMap.value[a.id]
    const bRank = semanticRankMap.value[b.id]
    if (typeof aRank === 'number' && typeof bRank === 'number' && aRank !== bRank) {
      return aRank - bRank
    }
    if (typeof aRank === 'number' && typeof bRank !== 'number') return -1
    if (typeof aRank !== 'number' && typeof bRank === 'number') return 1
    return 0
  })
})

const totalPages = computed(() => {
  const total = Math.ceil(filteredCandidates.value.length / PAGE_SIZE)
  return Math.max(1, total)
})

const pagedCandidates = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  const end = start + PAGE_SIZE
  return filteredCandidates.value.slice(start, end)
})

const pageRangeText = computed(() => {
  const total = filteredCandidates.value.length
  if (!total) {
    return '0-0'
  }
  const start = (currentPage.value - 1) * PAGE_SIZE + 1
  const end = Math.min(currentPage.value * PAGE_SIZE, total)
  return `${start}-${end}`
})

const goToPrevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value -= 1
  }
}

const goToNextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value += 1
  }
}

watch(filteredCandidates, (items) => {
  if (currentPage.value > totalPages.value) {
    currentPage.value = totalPages.value
  }
  if (currentPage.value < 1) {
    currentPage.value = 1
  }
  if (!selectedCandidate.value) return
  const exists = items.some((item) => item.id === selectedCandidate.value?.id)
  if (!exists) {
    selectedCandidate.value = items[0] || null
  }
})

watch(
  () => [selectedJdId.value, filterText.value, recruitmentFilter.value],
  () => {
    currentPage.value = 1
  }
)

const pushLog = (message) => {
  const ts = new Date().toLocaleTimeString()
  log.unshift({ ts, message })
  if (log.length > 60) log.pop()
}

const normalizeCandidate = (payload) => {
  const safePayload = payload && typeof payload === 'object' ? payload : {}
  const normalized = {
    id: safePayload.candidateId || safePayload.id || 'unknown',
    jdId: safePayload.jdId || safePayload.jd_id || '',
    status: safePayload.status || '',
    recruitmentStatus: safePayload.recruitmentStatus || 'TO_CONTACT',
    name: safePayload.name || '',
    title: safePayload.name || safePayload.title || '新入库候选人',
    summary: safePayload.summary || safePayload.note || '候选人已解析入库。',
    sourceFileName: safePayload.sourceFileName || '',
    contentType: safePayload.contentType || '',
    createdAt: safePayload.createdAt || safePayload.ingestedAt || '',
    emails: ensureStringArray(safePayload.emails),
    phones: ensureStringArray(safePayload.phones),
    educations: ensureStringArray(safePayload.educations),
    honors: ensureStringArray(safePayload.honors),
    links: ensureStringArray(safePayload.links)
  }
  if (Object.prototype.hasOwnProperty.call(safePayload, 'noVectorChunk')) {
    normalized.noVectorChunk = Boolean(safePayload.noVectorChunk)
  }
  if (Object.prototype.hasOwnProperty.call(safePayload, 'vectorStatus')) {
    normalized.vectorStatus = safePayload.vectorStatus || 'NONE'
  }
  return normalized
}

const applyCandidateUpdate = (candidate) => {
  const idx = events.findIndex((item) => item.id === candidate.id)
  if (idx >= 0) {
    events[idx] = { ...events[idx], ...candidate }
  } else {
    events.unshift(candidate)
    if (events.length > 200) events.pop()
  }
  if (selectedCandidate.value?.id === candidate.id) {
    selectedCandidate.value = { ...selectedCandidate.value, ...candidate }
  }
}

const refreshCandidateVectorFlags = async (jdId = selectedJdId.value) => {
  if (!jdId) return
  try {
    const resp = await fetch(`/api/candidates?jdId=${jdId}`)
    if (!resp.ok) return
    const data = await resp.json()
    if (!Array.isArray(data)) return

    const vectorStateById = new Map()
    for (const payload of data) {
      const id = payload?.candidateId || payload?.id
      if (!id) continue
      if (!Object.prototype.hasOwnProperty.call(payload, 'noVectorChunk') &&
          !Object.prototype.hasOwnProperty.call(payload, 'vectorStatus')) {
        continue
      }
      vectorStateById.set(id, {
        noVectorChunk: Boolean(payload?.noVectorChunk),
        vectorStatus: payload?.vectorStatus || 'NONE'
      })
    }
    if (!vectorStateById.size) return

    for (let i = 0; i < events.length; i += 1) {
      const item = events[i]
      if (!vectorStateById.has(item.id)) continue
      const next = vectorStateById.get(item.id)
      if (item.noVectorChunk !== next.noVectorChunk || item.vectorStatus !== next.vectorStatus) {
        events[i] = {
          ...item,
          noVectorChunk: next.noVectorChunk,
          vectorStatus: next.vectorStatus
        }
      }
    }
    if (selectedCandidate.value && vectorStateById.has(selectedCandidate.value.id)) {
      const next = vectorStateById.get(selectedCandidate.value.id)
      selectedCandidate.value = {
        ...selectedCandidate.value,
        noVectorChunk: next.noVectorChunk,
        vectorStatus: next.vectorStatus
      }
    }
  } catch (_) {
    // Ignore transient polling failures.
  }
}

const stopVectorFlagPolling = () => {
  if (vectorFlagPollTimer) {
    clearInterval(vectorFlagPollTimer)
    vectorFlagPollTimer = null
  }
}

const startVectorFlagPolling = () => {
  stopVectorFlagPolling()
  if (!selectedJdId.value) return
  vectorFlagPollTimer = setInterval(() => {
    refreshCandidateVectorFlags()
  }, 5000)
}

const connect = () => {
  if (isConnected.value) return
  isManualDisconnect.value = false
  const url = `${sseUrl}${sseUrl.includes('?') ? '&' : '?'}ts=${Date.now()}`
  source = new EventSource(url)

  source.onopen = () => {
    isConnected.value = true
    pushLog(`SSE 已连接: ${sseUrl}`)
  }

  source.addEventListener('candidate', (event) => {
    try {
      const payload = JSON.parse(event.data)
      const candidate = normalizeCandidate(payload)
      const belongsToSelectedJd = !selectedJdId.value || !candidate.jdId || candidate.jdId === selectedJdId.value
      if (belongsToSelectedJd) {
        applyCandidateUpdate(candidate)
        selectedCandidate.value = candidate
      }
      pushLog(`收到候选人: ${candidate.id}`)
      scheduleJdRefresh()
    } catch (err) {
      pushLog(`解析失败: ${err}`)
    }
  })

  source.addEventListener('vector', (event) => {
    try {
      const payload = JSON.parse(event.data)
      const candidateId = payload?.candidateId || payload?.id
      if (!candidateId) return
      const jdId = payload?.jdId || payload?.jd_id || ''
      if (selectedJdId.value && jdId && jdId !== selectedJdId.value) return

      const idx = events.findIndex((item) => item.id === candidateId)
      if (idx >= 0) {
        events[idx] = {
          ...events[idx],
          noVectorChunk: false,
          vectorStatus: 'READY'
        }
      }
      if (selectedCandidate.value?.id === candidateId) {
        selectedCandidate.value = {
          ...selectedCandidate.value,
          noVectorChunk: false,
          vectorStatus: 'READY'
        }
      }
      refreshCandidateVectorFlags(jdId || selectedJdId.value)
      scheduleSemanticRefresh(120)
      pushLog(`向量完成: ${candidateId}`)
    } catch (err) {
      pushLog(`向量事件解析失败: ${err}`)
    }
  })

  source.onerror = () => {
    if (isManualDisconnect.value) {
      return
    }
    pushLog('SSE 连接异常，3 秒后重连')
    disconnect(false)
    if (!reconnectTimer) {
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        connect()
      }, 3000)
    }
  }
}

const disconnect = (manual = true) => {
  if (manual) {
    isManualDisconnect.value = true
  }
  if (source) {
    source.close()
    source = null
  }
  if (isConnected.value) {
    pushLog('SSE 已断开')
  }
  isConnected.value = false
}

const selectCandidate = (item) => {
  recruitmentMessage.value = ''
  selectedCandidate.value = item
}

const setRecruitmentStatus = async (candidate, recruitmentStatus) => {
  if (!candidate?.id || !recruitmentStatus) return
  if (candidate.recruitmentStatus === recruitmentStatus) return
  recruitmentUpdatingId.value = candidate.id
  recruitmentMessage.value = ''
  try {
    const resp = await fetch(`/api/candidates/${candidate.id}/recruitment-status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ recruitmentStatus })
    })
    if (!resp.ok) throw new Error('状态更新失败')
    const updated = normalizeCandidate(await resp.json())
    applyCandidateUpdate(updated)
    selectedCandidate.value = { ...selectedCandidate.value, ...updated }
    recruitmentMessage.value = `已更新为${recruitmentStatusLabel(recruitmentStatus)}`
  } catch (err) {
    recruitmentMessage.value = err.message
  } finally {
    recruitmentUpdatingId.value = ''
  }
}

const refreshJds = async () => {
  jdMessage.value = ''
  try {
    const resp = await fetch('/api/jds')
    if (!resp.ok) throw new Error('加载 JD 失败')
    const data = await resp.json()
    jds.value = Array.isArray(data) ? data : []
    const hasSelectedJd = jds.value.some((jd) => jd.id === selectedJdId.value)
    if (selectedJdId.value && !hasSelectedJd) {
      if (jds.value.length) {
        selectJd(jds.value[0])
      } else {
        clearSelectedJd()
      }
      return
    }
    if (!selectedJdId.value && jds.value.length) {
      selectJd(jds.value[0])
    }
  } catch (err) {
    jdMessage.value = `JD 加载失败: ${err.message}`
  }
}

const loadCandidatesForJd = async (jdId) => {
  const requestSeq = ++candidateLoadSeq
  if (!jdId) {
    resetSemanticState(true)
    stopVectorFlagPolling()
    events.splice(0, events.length)
    currentPage.value = 1
    selectedCandidate.value = null
    return
  }
  try {
    const resp = await fetch(`/api/candidates?jdId=${jdId}`)
    if (!resp.ok) throw new Error('加载候选人失败')
    const data = await resp.json()
    if (requestSeq !== candidateLoadSeq) return
    events.splice(0, events.length, ...(Array.isArray(data) ? data.map(normalizeCandidate) : []))
    currentPage.value = 1
    selectedCandidate.value = events[0] || null
    startVectorFlagPolling()
    applySemanticTuningFromJd()
    await refreshSemanticRanking()
  } catch (err) {
    if (requestSeq !== candidateLoadSeq) return
    resetSemanticState(false)
    events.splice(0, events.length)
    currentPage.value = 1
    selectedCandidate.value = null
    jdMessage.value = `候选人加载失败: ${err.message}`
    pushLog(`候选人加载失败: ${err.message}`)
  }
}

const scheduleJdRefresh = () => {
  if (jdRefreshTimer) return
  jdRefreshTimer = setTimeout(async () => {
    jdRefreshTimer = null
    await refreshJds()
    if (selectedJdId.value) {
      await loadCandidatesForJd(selectedJdId.value)
    }
  }, 1200)
}

const resetUploadState = () => {
  uploadFiles.value = []
  uploadMessage.value = ''
  isDragActive.value = false
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

const clearSelectedJd = () => {
  stopVectorFlagPolling()
  resetUploadState()
  selectedJdId.value = ''
  jdTitle.value = ''
  jdText.value = ''
  cancelEditJd()
  loadCandidatesForJd('')
}

const selectJd = (jd) => {
  if (selectedJdId.value === jd.id) {
    clearSelectedJd()
    return
  }
  resetUploadState()
  selectedJdId.value = jd.id
  jdTitle.value = jd.title || ''
  jdText.value = jd.content || ''
  loadCandidatesForJd(jd.id)
}

const createJd = async () => {
  if (!jdTitle.value.trim()) return
  jdSaving.value = true
  jdMessage.value = ''
  try {
    const payload = {
      title: jdTitle.value.trim(),
      content: jdText.value.trim()
    }
    const url = '/api/jds'
    const method = 'POST'
    const resp = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (!resp.ok) throw new Error('保存 JD 失败')
    const saved = await resp.json()
    selectedJdId.value = saved.id
    await refreshJds()
    jdTitle.value = ''
    jdText.value = ''
    showCreateJd.value = false
    jdMessage.value = '已保存'
    await loadCandidatesForJd(saved.id)
  } catch (err) {
    jdMessage.value = err.message
  } finally {
    jdSaving.value = false
  }
}

const openCreateJd = () => {
  jdTitle.value = ''
  jdText.value = ''
  showCreateJd.value = true
}

const closeCreateJd = () => {
  showCreateJd.value = false
}

const startEditJd = (jd) => {
  editingJdId.value = jd.id
  editTitle.value = jd.title || ''
  editContent.value = jd.content || ''
}

const cancelEditJd = () => {
  editingJdId.value = ''
  editTitle.value = ''
  editContent.value = ''
}

const saveEditJd = async (jd) => {
  if (!editTitle.value.trim()) return
  jdSaving.value = true
  jdMessage.value = ''
  try {
    const payload = {
      title: editTitle.value.trim(),
      content: editContent.value.trim()
    }
    const resp = await fetch(`/api/jds/${jd.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (!resp.ok) throw new Error('保存 JD 失败')
    await refreshJds()
    cancelEditJd()
  } catch (err) {
    jdMessage.value = err.message
  } finally {
    jdSaving.value = false
  }
}

const deleteJd = async (jd) => {
  if (!confirm(`删除 JD: ${jd.title} ?\n\n这会一并删除该 JD 下的候选人、上传批次和向量数据。`)) return
  jdSaving.value = true
  jdMessage.value = ''
  try {
    const resp = await fetch(`/api/jds/${jd.id}`, { method: 'DELETE' })
    if (!resp.ok && resp.status !== 204) throw new Error('删除 JD 失败')
    jds.value = jds.value.filter((item) => item.id !== jd.id)
    if (selectedJdId.value === jd.id) {
      clearSelectedJd()
    } else if (!selectedJdId.value && jds.value.length) {
      selectJd(jds.value[0])
    }
    await refreshJds()
  } catch (err) {
    jdMessage.value = err.message
  } finally {
    jdSaving.value = false
  }
}

const onFileChange = (e) => {
  if (uploading.value) return
  const files = Array.from(e.target.files || [])
  uploadFiles.value = files
  if (files.length) {
    uploadResume()
  }
}

const onDragOver = () => {
  if (!selectedJdId.value) return
  isDragActive.value = true
}

const onDragLeave = () => {
  isDragActive.value = false
}

const onDrop = (e) => {
  isDragActive.value = false
  if (!selectedJdId.value) {
    uploadMessage.value = '请先选择 JD'
    return
  }
  if (uploading.value) return
  const files = Array.from(e.dataTransfer?.files || [])
  if (files.length) {
    uploadFiles.value = files
    uploadResume()
  }
}

const openFilePicker = () => {
  if (!selectedJdId.value) {
    uploadMessage.value = '请先选择 JD'
    return
  }
  if (uploading.value || !fileInputRef.value) return
  fileInputRef.value.value = ''
  fileInputRef.value.click()
}

const uploadResume = async () => {
  if (uploading.value || !uploadFiles.value.length || !selectedJdId.value) return
  uploading.value = true
  uploadMessage.value = ''
  try {
    const isZip = uploadFiles.value.length === 1 && uploadFiles.value[0].name.toLowerCase().endsWith('.zip')
    const formData = new FormData()
    formData.append('jdId', selectedJdId.value)
    let url = '/api/uploads/resumes'
    if (isZip) {
      url = '/api/uploads/zip'
      formData.append('zipFile', uploadFiles.value[0])
    } else {
      uploadFiles.value.forEach((file) => {
        formData.append('files', file)
      })
    }
    const resp = await fetch(url, {
      method: 'POST',
      body: formData
    })
    if (!resp.ok) throw new Error('上传失败')
    const data = await resp.json()
    if (isZip) {
      uploadMessage.value = `上传成功: batch ${data.batchId}, 共 ${data.totalFiles} 份`
    } else {
      const files = Array.isArray(data?.files) ? data.files : []
      const duplicateCount = files.filter((f) => f.status === 'DUPLICATE').length
      const doneCount = files.filter((f) => f.status === 'DONE').length
      const failedCount = files.filter((f) => f.status === 'FAILED').length
      const queuedCount = files.filter((f) => f.status === 'QUEUED').length
      let msg = `上传成功: batch ${data.batchId}, 共 ${files.length || uploadFiles.value.length} 份`
      if (queuedCount) msg += `，入队 ${queuedCount}`
      if (duplicateCount) msg += `，重复 ${duplicateCount}`
      if (failedCount) msg += `，失败 ${failedCount}`
      if (doneCount) msg += `，新增 ${doneCount}`
      uploadMessage.value = msg
    }
    uploadFiles.value = []
    await refreshJds()
  } catch (err) {
    uploadMessage.value = err.message
  } finally {
    uploading.value = false
  }
}

onMounted(() => {
  refreshJds()
  connect()
})

onBeforeUnmount(() => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (jdRefreshTimer) {
    clearTimeout(jdRefreshTimer)
    jdRefreshTimer = null
  }
  stopSemanticRefreshTimer()
  stopVectorFlagPolling()
  disconnect()
})
</script>

<template>
  <main v-if="!authReady" class="auth-shell">
    <div class="auth-panel">
      <h1>CVect</h1>
      <p>正在验证登录状态...</p>
    </div>
  </main>

  <main v-else-if="!currentUser" class="auth-shell">
    <form class="auth-panel" @submit.prevent="submitLogin">
      <h1>CVect</h1>
      <label class="field">
        <span>租户 ID</span>
        <input v-model="loginTenantId" type="text" placeholder="默认租户可留空" autocomplete="organization" />
      </label>
      <label class="field">
        <span>用户名</span>
        <input v-model="loginUsername" type="text" autocomplete="username" />
      </label>
      <label class="field">
        <span>密码</span>
        <input v-model="loginPassword" type="password" autocomplete="current-password" />
      </label>
      <button type="submit" :disabled="authLoading || !loginUsername.trim() || !loginPassword">
        {{ authLoading ? '登录中...' : '登录' }}
      </button>
      <p class="muted" v-if="loginMessage">{{ loginMessage }}</p>
    </form>
  </main>

  <main v-else class="workspace" :class="roleThemeClass">
    <section class="role-hero">
      <div class="role-hero-main">
        <div class="role-eyebrow">
          <span class="role-mark">{{ roleProfile.shortLabel }}</span>
          <span>{{ roleProfile.label }}</span>
          <span>{{ roleProfile.scope }}</span>
        </div>
        <h1 class="title">{{ roleProfile.title }}</h1>
        <p class="subtitle">{{ roleProfile.subtitle }}</p>
        <div class="role-actions">
          <button class="secondary small icon-button" @click="refreshJds">
            <span class="button-icon" aria-hidden="true">R</span>
            <span>刷新 JD</span>
          </button>
          <button v-if="canWriteJd" class="small icon-button" @click="openCreateJd">
            <span class="button-icon" aria-hidden="true">+</span>
            <span>新建 JD</span>
          </button>
          <button
            v-if="canReadAudit"
            class="secondary small icon-button"
            :disabled="auditLoading"
            @click="loadAuditLogs"
          >
            <span class="button-icon" aria-hidden="true">A</span>
            <span>{{ auditLoading ? '审计刷新中' : '刷新审计' }}</span>
          </button>
        </div>
      </div>
      <div class="status-panel role-status">
        <div class="status-row">
          <span>{{ currentUser.displayName || currentUser.username }}</span>
          <button class="secondary small icon-button" @click="logout">
            <span class="button-icon" aria-hidden="true">X</span>
            <span>退出</span>
          </button>
        </div>
        <div class="status-row">
          <span>角色视角</span>
          <span class="badge">{{ roleScopeLabel }}</span>
        </div>
        <div class="status-row">
          <span>连接状态</span>
          <span class="badge" :class="isConnected ? 'live' : 'offline'">
            {{ isConnected ? 'Live' : 'Offline' }}
          </span>
        </div>
        <div class="permission-grid">
          <div
            v-for="capability in roleCapabilities"
            :key="capability.label"
            class="capability-pill"
            :class="{ available: capability.available }"
          >
            <span class="capability-dot"></span>
            <span>{{ capability.label }}</span>
            <strong>{{ capability.text }}</strong>
          </div>
        </div>
      </div>
    </section>

    <section class="role-dashboard" aria-label="角色指标">
      <article v-for="metric in roleMetrics" :key="metric.label" class="role-metric">
        <span class="metric-label">{{ metric.label }}</span>
        <strong class="metric-value">{{ metric.value }}</strong>
        <span class="metric-caption">{{ metric.caption }}</span>
      </article>
    </section>

    <section class="data-view-strip">
      <div>
        <span class="section-kicker">数据视图</span>
        <h2>{{ dataScopeSummary.title }}</h2>
        <p>{{ dataScopeSummary.description }}</p>
      </div>
      <div class="scope-chips">
        <span v-for="chip in dataScopeSummary.chips" :key="chip" class="scope-chip">{{ chip }}</span>
      </div>
    </section>

    <section class="role-workflow" :class="`workflow-${currentRoleKey}`">
      <div class="workflow-heading">
        <span class="section-kicker">{{ roleWorkflow.kicker }}</span>
        <h2>{{ roleWorkflow.title }}</h2>
        <p>{{ roleWorkflow.description }}</p>
      </div>

      <div v-if="currentRoleKey === 'hr'" class="pipeline-board">
        <button
          v-for="option in recruitmentStatusOptions"
          :key="option.value"
          class="pipeline-lane"
          :class="{ active: recruitmentFilter === option.value }"
          @click="setRecruitmentFilter(option.value)"
        >
          <span>{{ option.label }}</span>
          <strong>{{ statusCounts[option.value] || 0 }}</strong>
          <small>{{ pipelineCaption(option.value) }}</small>
        </button>
        <button class="pipeline-lane clear-lane" :class="{ active: !recruitmentFilter }" @click="setRecruitmentFilter('')">
          <span>全部候选人</span>
          <strong>{{ scopedCandidates.length }}</strong>
          <small>取消状态过滤</small>
        </button>
      </div>

      <div v-else-if="currentRoleKey === 'recruiter'" class="execution-board">
        <div class="execution-primary">
          <span>当前执行 JD</span>
          <strong>{{ selectedJd?.title || '未选择 JD' }}</strong>
          <p>{{ selectedJdId ? '上传和状态推进都会绑定这个 JD。' : '先从我的 JD 列表中选择一个岗位。' }}</p>
          <div class="execution-actions">
            <button class="small icon-button" :disabled="!canUploadResume || !selectedJdId" @click="openFilePicker">
              <span class="button-icon" aria-hidden="true">U</span>
              <span>上传简历</span>
            </button>
            <button class="secondary small icon-button" @click="setRecruitmentFilter('TO_CONTACT')">
              <span class="button-icon" aria-hidden="true">F</span>
              <span>{{ recruitmentStatusLabel('TO_CONTACT') }}</span>
            </button>
          </div>
        </div>
        <div class="execution-queue">
          <button
            v-for="item in recruiterQueue"
            :key="item.id"
            class="queue-item"
            :class="{ active: selectedCandidate?.id === item.id }"
            @click="selectCandidate(item)"
          >
            <span>{{ recruitmentStatusLabel(item.recruitmentStatus) }}</span>
            <strong>{{ item.title }}</strong>
            <small>{{ item.sourceFileName || item.id }}</small>
          </button>
          <div v-if="!recruiterQueue.length" class="empty">
            {{ selectedJdId ? '当前 JD 暂无待处理候选人。' : '选择 JD 后显示待跟进候选人。' }}
          </div>
        </div>
      </div>

      <div v-else class="governance-board">
        <article v-for="item in governanceItems" :key="item.label">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.caption }}</small>
        </article>
      </div>
    </section>

    <section class="jd-board">
      <div class="panel jd-list">
        <div class="panel-header">
          <div>
            <h3>{{ jdListTitle }}</h3>
            <p class="section-subtitle">{{ jdListSubtitle }}</p>
          </div>
          <div class="jd-header-actions">
            <button class="secondary small icon-button" @click="refreshJds">
              <span class="button-icon" aria-hidden="true">R</span>
              <span>刷新</span>
            </button>
            <button v-if="canWriteJd" class="small icon-button" @click="openCreateJd">
              <span class="button-icon" aria-hidden="true">+</span>
              <span>新建</span>
            </button>
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
                  <button v-if="canWriteJd" class="ghost" @click.stop="startEditJd(jd)">编辑</button>
                  <button v-if="canDeleteJd" class="ghost danger" @click.stop="deleteJd(jd)">删除</button>
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
          <div v-if="!jds.length" class="empty">{{ emptyJdText }}</div>
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

      <div class="modal-backdrop" v-if="showCreateJd && canWriteJd" @click="closeCreateJd">
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
        v-if="canUploadResume"
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
            <span v-if="selectedJdId">{{ RESUME_UPLOAD_HELP_TEXT }}</span>
            <span v-else>请先选择 JD</span>
          </div>
          <div class="dropzone-files" v-if="uploadFiles.length">
            {{ uploadFiles.map((f) => f.name).join(', ') }}
          </div>
        </div>
        <input
          ref="fileInputRef"
          type="file"
          multiple
          class="file-input"
          :accept="RESUME_UPLOAD_ACCEPT"
          @change="onFileChange"
        />
        <p class="muted" v-if="uploadMessage">{{ uploadMessage }}</p>
      </div>
    </section>

    <section v-if="canReadAudit" class="audit-strip">
      <div class="panel-header">
        <div>
          <span class="section-kicker">审计</span>
          <h3>租户操作记录</h3>
        </div>
        <button class="secondary small icon-button" :disabled="auditLoading" @click="loadAuditLogs">
          <span class="button-icon" aria-hidden="true">R</span>
          <span>{{ auditLoading ? '刷新中' : '刷新' }}</span>
        </button>
      </div>
      <div class="audit-list">
        <article v-for="entry in auditLogs" :key="entry.id" class="audit-entry">
          <div>
            <strong>{{ entry.action || 'UNKNOWN_ACTION' }}</strong>
            <span>{{ entry.target || entry.requestPath || 'unknown target' }}</span>
          </div>
          <div class="audit-meta">
            <span>{{ entry.username || 'system' }}</span>
            <span>{{ entry.createdAt || '—' }}</span>
            <span class="audit-status" :class="entry.status === 'error' ? 'error' : 'success'">
              {{ entry.status || 'success' }}
            </span>
          </div>
        </article>
        <div v-if="!auditLogs.length" class="empty">
          {{ auditLoading ? '审计加载中...' : '暂无审计记录' }}
        </div>
      </div>
      <p class="muted" v-if="auditMessage">{{ auditMessage }}</p>
    </section>

    <section class="candidate-view-header">
      <div>
        <span class="section-kicker">候选人</span>
        <h2>{{ candidateViewTitle }}</h2>
        <p>{{ candidateViewDescription }}</p>
      </div>
      <div class="candidate-count-pill">
        <span>当前筛选</span>
        <strong>{{ filteredCandidates.length }}</strong>
        <span>总计 {{ totalCandidatesCount }}</span>
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
      <button
        class="secondary small"
        :disabled="manualRefreshing || !selectedJdId"
        @click="refreshSelectedJdCandidates"
      >
        {{ manualRefreshing ? '刷新中...' : '刷新当前 JD' }}
      </button>
      <span v-if="manualRefreshStatus" class="muted">{{ manualRefreshStatus }}</span>
      <span class="filter-stats">显示 {{ pageRangeText }} / {{ filteredCandidates.length }}（总计 {{ totalCandidatesCount }}）</span>
    </section>

    <SemanticPanel
      v-if="canRunSearch"
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
            <span class="recruitment-badge" :class="`status-${effectiveRecruitmentStatus(item.recruitmentStatus)}`">
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

      <aside class="detail" :class="{ empty: !selectedCandidate }">
        <template v-if="selectedCandidate">
          <div class="detail-header">
            <h2>候选人详情</h2>
            <span class="detail-id">{{ selectedCandidate.id }}</span>
          </div>
          <div v-if="candidateDetailLoading" class="detail-section detail-empty-state">
            <p class="muted">候选人详情加载中...</p>
          </div>
          <div v-else-if="candidateDetailError" class="detail-section detail-empty-state">
            <p class="muted">{{ candidateDetailError }}</p>
            <button class="secondary small" @click="retryCandidateDetail">
              重试
            </button>
          </div>
          <div class="detail-section">
            <h4>姓名</h4>
            <p>{{ selectedCandidate.name || '未识别' }}</p>
          </div>
          <div class="detail-section">
            <h4>上传文件元数据</h4>
            <ul>
              <li v-if="selectedCandidate.sourceFileName">文件名：{{ selectedCandidate.sourceFileName }}</li>
              <li v-if="selectedCandidate.contentType">类型：{{ selectedCandidate.contentType }}</li>
              <li v-if="selectedCandidate.fileSizeBytes != null">大小：{{ formatFileSize(selectedCandidate.fileSizeBytes) }}</li>
              <li v-if="selectedCandidate.parsedCharCount != null">解析字符数：{{ selectedCandidate.parsedCharCount }}</li>
              <li v-if="selectedCandidate.truncated != null">是否截断：{{ selectedCandidate.truncated ? '是' : '否' }}</li>
              <li v-if="!hasUploadFileMetadata(selectedCandidate)" class="muted">暂无上传文件元数据</li>
            </ul>
          </div>
          <div class="detail-section">
            <h4>招聘进度</h4>
            <div class="recruitment-actions">
              <button
                v-for="option in recruitmentStatusOptions"
                :key="option.value"
                class="status-btn"
                :class="{ active: effectiveRecruitmentStatus(selectedCandidate.recruitmentStatus) === option.value }"
                :disabled="!canUpdateCandidateStatus || recruitmentUpdatingId === selectedCandidate.id"
                @click="setRecruitmentStatus(selectedCandidate, option.value)"
              >
                {{ option.label }}
              </button>
            </div>
            <p class="muted" v-if="!canUpdateCandidateStatus">当前角色仅可查看招聘进度。</p>
            <p class="muted" v-if="recruitmentMessage">{{ recruitmentMessage }}</p>
          </div>
          <div class="detail-section">
            <h4>匹配信息</h4>
            <ul>
              <li v-if="candidateMatchScore(selectedCandidate) != null">
                匹配总分：{{ formatScorePercent(candidateMatchScore(selectedCandidate)) }}
              </li>
              <li v-if="selectedCandidate.persistedMatchScoredAt">
                评分时间：{{ formatDateTime(selectedCandidate.persistedMatchScoredAt) }}
              </li>
              <li v-if="selectedCandidate.baselineExperienceScore != null">
                Experience 匹配：{{ formatScorePercent(selectedCandidate.baselineExperienceScore) }}
              </li>
              <li v-if="selectedCandidate.baselineSkillScore != null">
                Skill 匹配：{{ formatScorePercent(selectedCandidate.baselineSkillScore) }}
              </li>
              <li v-if="!hasCandidateMatchInfo(selectedCandidate)" class="muted">暂无匹配信息</li>
            </ul>
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
        </template>
        <template v-else>
          <div class="detail-header">
            <h2>候选人详情</h2>
            <span class="detail-id">待选择</span>
          </div>
          <div class="detail-section detail-empty-state">
            <p class="muted">从左侧候选人列表选择一位候选人，这里会展示详情与招聘进度。</p>
          </div>
        </template>
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
import {
  DEFAULT_RECRUITMENT_STATUS,
  RECRUITMENT_STATUS_OPTIONS,
  normalizeRecruitmentStatus,
  recruitmentStatusLabel
} from './utils/recruitmentStatus'
import {
  isZipResumeUpload,
  RESUME_UPLOAD_ACCEPT,
  RESUME_UPLOAD_HELP_TEXT,
  validateResumeUploadFiles
} from './utils/resumeUploadFormats'
import {
  apiFetch,
  authLogin,
  authLogout,
  authMe,
  clearAccessToken,
  getAccessToken,
  getAccessTokenExpiresAt
} from './api/http'

const sseUrl = import.meta.env.VITE_SSE_URL || '/api/candidates/stream'

const isConnected = ref(false)
const authReady = ref(false)
const authLoading = ref(false)
const currentUser = ref(null)
const loginTenantId = ref('')
const loginUsername = ref('demo')
const loginPassword = ref('')
const loginMessage = ref('')
const events = reactive([])
const log = reactive([])
const selectedCandidate = ref(null)
const candidateDetailLoading = ref(false)
const candidateDetailError = ref('')
const isManualDisconnect = ref(false)
let reconnectTimer = null
let authExpiryTimer = null

const selectedJd = computed(() => {
  return jds.value.find((jd) => jd.id === selectedJdId.value) || null
})

const currentPermissions = computed(() => new Set(currentUser.value?.permissions || []))
const hasPermission = (permission) => currentPermissions.value.has(permission)
const currentRoles = computed(() => currentUser.value?.roles || [])

const roleProfiles = {
  owner: {
    key: 'owner',
    className: 'workspace-owner',
    shortLabel: 'OWN',
    label: '企业管理员',
    scope: '全租户治理',
    scopeLabel: '企业管理员 / 全租户',
    title: '企业招聘治理台',
    subtitle: '面向租户全局管理 JD、候选人、审计和权限能力，优先关注数据质量与操作风险。'
  },
  hr: {
    key: 'hr',
    className: 'workspace-hr',
    shortLabel: 'HR',
    label: '招聘负责人',
    scope: '全租户招聘',
    scopeLabel: '招聘负责人 / 全租户',
    title: '招聘负责人工作台',
    subtitle: '从招聘管道角度统筹岗位、候选人和语义匹配结果，优先关注推进效率。'
  },
  recruiter: {
    key: 'recruiter',
    className: 'workspace-recruiter',
    shortLabel: 'REC',
    label: '招聘专员',
    scope: '我的 JD',
    scopeLabel: '招聘专员 / 我的 JD',
    title: '我的招聘执行台',
    subtitle: '只展示由你负责的 JD 与候选人，聚焦简历入库、筛选和状态跟进。'
  },
  limited: {
    key: 'limited',
    className: 'workspace-limited',
    shortLabel: 'RO',
    label: '受限用户',
    scope: '权限控制',
    scopeLabel: '受限视角',
    title: '受限招聘视图',
    subtitle: '当前账号仅显示已授权的数据和功能入口。'
  }
}

const currentRoleKey = computed(() => {
  if (currentRoles.value.includes('OWNER')) return 'owner'
  if (currentRoles.value.includes('HR_MANAGER')) return 'hr'
  if (currentRoles.value.includes('RECRUITER')) return 'recruiter'
  return 'limited'
})

const roleProfile = computed(() => roleProfiles[currentRoleKey.value] || roleProfiles.limited)
const roleThemeClass = computed(() => roleProfile.value.className)
const roleScopeLabel = computed(() => {
  return roleProfile.value.scopeLabel
})
const canWriteJd = computed(() => hasPermission('JD_WRITE'))
const canDeleteJd = computed(() => hasPermission('JD_DELETE'))
const canUploadResume = computed(() => hasPermission('RESUME_UPLOAD'))
const canUpdateCandidateStatus = computed(() => hasPermission('CANDIDATE_UPDATE_STATUS'))
const canRunSearch = computed(() => hasPermission('SEARCH_RUN'))
const canReadAudit = computed(() => hasPermission('AUDIT_READ'))
const canManageUsers = computed(() => hasPermission('USER_MANAGE'))
const canManageRoles = computed(() => hasPermission('ROLE_MANAGE'))

const roleCapabilities = computed(() => [
  {
    label: 'JD 管理',
    available: canWriteJd.value,
    text: canDeleteJd.value ? '新建/编辑/删除' : canWriteJd.value ? '新建/编辑' : '只读'
  },
  {
    label: '简历入库',
    available: canUploadResume.value,
    text: canUploadResume.value ? '可上传' : '无入口'
  },
  {
    label: '候选人状态',
    available: canUpdateCandidateStatus.value,
    text: canUpdateCandidateStatus.value ? '可推进' : '只读'
  },
  {
    label: '语义匹配',
    available: canRunSearch.value,
    text: canRunSearch.value ? '可排序' : '不可用'
  },
  {
    label: '账号权限',
    available: canManageUsers.value || canManageRoles.value,
    text: canManageUsers.value || canManageRoles.value ? '已授权' : '无入口'
  },
  {
    label: '审计',
    available: canReadAudit.value,
    text: canReadAudit.value ? '可查看' : '隐藏'
  }
])

const filterText = ref('')
const recruitmentFilter = ref('')

let source = null
let jdRefreshTimer = null
let jdRefreshNeedsCandidateReload = false
let jdRefreshCandidateReloadTarget = ''
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
const auditLogs = ref([])
const auditLoading = ref(false)
const auditMessage = ref('')
const isDragActive = ref(false)
const fileInputRef = ref(null)
const editingJdId = ref('')
const editTitle = ref('')
const editContent = ref('')
const recruitmentUpdatingId = ref('')
const recruitmentMessage = ref('')
const manualRefreshing = ref(false)
const manualRefreshStatus = ref('')
const PAGE_SIZE = 20
const currentPage = ref(1)

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

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
  reconcileSemanticRanking,
  scheduleSemanticRefresh,
  refreshSemanticRanking,
  resetSemanticState,
  stopSemanticRefreshTimer
} = useSemanticMatching({ events, selectedJdId, selectedJd })

const recruitmentStatusOptions = RECRUITMENT_STATUS_OPTIONS

const effectiveRecruitmentStatus = (status) => {
  return normalizeRecruitmentStatus(status)
}

const parseScore = (value) => {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return null
    const parsed = Number(trimmed)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

const parseNullableInteger = (value) => {
  if (typeof value === 'number' && Number.isFinite(value)) return Math.trunc(value)
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return null
    const parsed = Number(trimmed)
    return Number.isFinite(parsed) ? Math.trunc(parsed) : null
  }
  return null
}

const parseNullableBoolean = (value) => {
  if (value === true || value === false) return value
  return null
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

const candidateMatchScore = (candidate) => {
  if (!candidate) return null
  if (typeof candidate.persistedMatchScore === 'number' && Number.isFinite(candidate.persistedMatchScore)) {
    return candidate.persistedMatchScore
  }
  return parseScore(candidate.baselineMatchScore)
}

const hasCandidateMatchInfo = (candidate) => {
  if (!candidate) return false
  return candidateMatchScore(candidate) != null
    || Boolean(candidate.persistedMatchScoredAt)
    || parseScore(candidate.baselineExperienceScore) != null
    || parseScore(candidate.baselineSkillScore) != null
}

const hasUploadFileMetadata = (candidate) => {
  if (!candidate) return false
  return Boolean(candidate.sourceFileName)
    || Boolean(candidate.contentType)
    || candidate.fileSizeBytes != null
    || candidate.parsedCharCount != null
    || candidate.truncated != null
}

const formatScorePercent = (value) => {
  const score = parseScore(value)
  return score == null ? '暂无数据' : `${Math.round(score * 100)}%`
}

const formatFileSize = (value) => {
  const bytes = parseNullableInteger(value)
  if (bytes == null || bytes < 0) return '暂无数据'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const formatDateTime = (value) => {
  if (!value) return ''
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return String(value)
  }
  return parsed.toLocaleString()
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

const sortCandidates = (items) => {
  return [...items].sort((a, b) => {
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
}

const scopedCandidates = computed(() => {
  const keyword = filterText.value.trim().toLowerCase()
  const keywordParts = keyword ? keyword.split(/\s+/).filter(Boolean) : []
  const scoped = events.filter((item) => {
    if (selectedJdId.value && item.jdId && item.jdId !== selectedJdId.value) {
      return false
    }

    if (!keywordParts.length) return true
    const hay = candidateSearchText(item)
    return keywordParts.every((part) => hay.includes(part))
  })

  return sortCandidates(scoped)
})

const filteredCandidates = computed(() => {
  if (!recruitmentFilter.value) {
    return scopedCandidates.value
  }
  return scopedCandidates.value.filter((item) => effectiveRecruitmentStatus(item.recruitmentStatus) === recruitmentFilter.value)
})

const totalPages = computed(() => {
  const total = Math.ceil(filteredCandidates.value.length / PAGE_SIZE)
  return Math.max(1, total)
})

const totalCandidatesCount = computed(() => {
  if (selectedJd.value && Number.isFinite(Number(selectedJd.value.candidateCount))) {
    return Number(selectedJd.value.candidateCount)
  }
  return events.length
})

const jdCandidateTotal = computed(() => {
  return jds.value.reduce((total, jd) => {
    const count = Number(jd.candidateCount)
    return total + (Number.isFinite(count) ? count : 0)
  }, 0)
})

const statusCounts = computed(() => {
  return scopedCandidates.value.reduce((counts, item) => {
    const status = effectiveRecruitmentStatus(item.recruitmentStatus)
    counts[status] = (counts[status] || 0) + 1
    return counts
  }, {})
})

const readyVectorCount = computed(() => {
  return events.filter((item) => item.vectorStatus === 'READY').length
})

const selectedJdCaption = computed(() => {
  return selectedJd.value ? selectedJd.value.title : '未选择 JD'
})

const roleMetrics = computed(() => {
  if (currentRoleKey.value === 'owner') {
    return [
      { label: '租户 JD', value: jds.value.length, caption: '当前租户可见' },
      { label: '候选人池', value: jdCandidateTotal.value || totalCandidatesCount.value, caption: '来自全租户 JD' },
      { label: '审计记录', value: auditLogs.value.length, caption: '最近加载' },
      { label: '账号权限', value: canManageUsers.value || canManageRoles.value ? '开启' : '关闭', caption: '用户/角色授权' }
    ]
  }
  if (currentRoleKey.value === 'hr') {
    return [
      { label: '招聘 JD', value: jds.value.length, caption: '全租户招聘视图' },
      { label: '当前候选人', value: totalCandidatesCount.value, caption: selectedJdCaption.value },
      { label: recruitmentStatusLabel('TO_INTERVIEW'), value: statusCounts.value.TO_INTERVIEW || 0, caption: '当前筛选范围' },
      { label: '语义就绪', value: readyVectorCount.value, caption: '可参与匹配' }
    ]
  }
  if (currentRoleKey.value === 'recruiter') {
    return [
      { label: '我的 JD', value: jds.value.length, caption: '按创建人过滤' },
      { label: '当前候选人', value: totalCandidatesCount.value, caption: selectedJdCaption.value },
      { label: recruitmentStatusLabel('TO_CONTACT'), value: statusCounts.value.TO_CONTACT || 0, caption: '当前 JD' },
      { label: '上传入口', value: canUploadResume.value ? '可用' : '隐藏', caption: '按权限展示' }
    ]
  }
  return [
    { label: '可见 JD', value: jds.value.length, caption: '权限过滤后' },
    { label: '候选人', value: totalCandidatesCount.value, caption: selectedJdCaption.value },
    { label: recruitmentStatusLabel('TO_CONTACT'), value: statusCounts.value.TO_CONTACT || 0, caption: '当前范围' },
    { label: '功能入口', value: roleCapabilities.value.filter((item) => item.available).length, caption: '已授权' }
  ]
})

const dataScopeSummary = computed(() => {
  if (currentRoleKey.value === 'owner') {
    return {
      title: '全租户治理视图',
      description: '接口返回当前租户内全部 JD 与候选人；审计日志仅在 AUDIT_READ 权限下加载。',
      chips: ['全部 JD', '全部候选人', '审计日志', '账号权限']
    }
  }
  if (currentRoleKey.value === 'hr') {
    return {
      title: '全租户招聘视图',
      description: '接口返回租户范围内招聘数据，页面聚焦 JD 管理、候选人推进和语义匹配。',
      chips: ['全部 JD', '全部候选人', '招聘状态', '语义匹配']
    }
  }
  if (currentRoleKey.value === 'recruiter') {
    return {
      title: '个人执行视图',
      description: '接口只返回由你创建或负责的 JD 及其候选人，删除和租户级治理入口不展示。',
      chips: ['我的 JD', '我的候选人', '简历上传', '状态跟进']
    }
  }
  return {
    title: '权限过滤视图',
    description: '页面只展示当前账号已授权的数据范围和功能入口。',
    chips: ['只读范围', '权限控制']
  }
})

const jdListTitle = computed(() => {
  if (currentRoleKey.value === 'owner') return '全租户 JD 管理'
  if (currentRoleKey.value === 'hr') return '招聘 JD 管道'
  if (currentRoleKey.value === 'recruiter') return '我的 JD 列表'
  return '可见 JD'
})

const jdListSubtitle = computed(() => {
  if (currentRoleKey.value === 'owner') return '用于检查租户内岗位覆盖和数据归属。'
  if (currentRoleKey.value === 'hr') return '用于统筹招聘需求、候选人池和推进节奏。'
  if (currentRoleKey.value === 'recruiter') return '仅展示你创建或负责的岗位。'
  return '按当前权限过滤后展示。'
})

const emptyJdText = computed(() => {
  if (currentRoleKey.value === 'recruiter') return '暂无你负责的 JD。'
  if (currentRoleKey.value === 'hr') return '当前租户暂无招聘 JD。'
  if (currentRoleKey.value === 'owner') return '当前租户暂无 JD。'
  return '暂无可见 JD。'
})

const candidateViewTitle = computed(() => {
  if (currentRoleKey.value === 'owner') return '全租户候选人视图'
  if (currentRoleKey.value === 'hr') return '招聘管道候选人'
  if (currentRoleKey.value === 'recruiter') return '我的候选人跟进'
  return '可见候选人'
})

const candidateViewDescription = computed(() => {
  if (currentRoleKey.value === 'owner') return '候选人列表跟随当前 JD 和筛选条件变化，用于抽查数据质量与状态分布。'
  if (currentRoleKey.value === 'hr') return '按岗位筛选候选人，结合语义匹配与招聘状态推进短名单。'
  if (currentRoleKey.value === 'recruiter') return '只处理你可见的候选人，上传、筛选和状态更新都绑定当前 JD。'
  return '候选人数据按当前账号权限返回。'
})

const roleWorkflow = computed(() => {
  if (currentRoleKey.value === 'hr') {
    return {
      kicker: '招聘管道',
      title: '按状态管理全租户候选人',
      description: '招聘负责人优先看状态分布，用快捷入口切换待联系、待面试和已拒绝视图。'
    }
  }
  if (currentRoleKey.value === 'recruiter') {
    return {
      kicker: '个人执行',
      title: '围绕当前 JD 处理简历和跟进',
      description: '招聘专员优先处理自己的岗位，上传简历、查看待联系候选人并推进状态。'
    }
  }
  return {
    kicker: '治理检查',
    title: '租户级数据和权限概览',
    description: '企业管理员优先查看权限、审计和租户数据质量，避免越权和脏数据扩散。'
  }
})

const pipelineCaption = (status) => {
  if (status === 'TO_CONTACT') return '待联系候选人'
  if (status === 'TO_INTERVIEW') return '进入面试推进'
  if (status === 'REJECTED') return '已拒绝候选人'
  return '当前状态'
}

const recruiterQueue = computed(() => {
  return scopedCandidates.value
    .filter((item) => ['TO_CONTACT', 'TO_INTERVIEW'].includes(effectiveRecruitmentStatus(item.recruitmentStatus)))
    .slice(0, 5)
})

const governanceItems = computed(() => [
  {
    label: '租户 JD',
    value: jds.value.length,
    caption: '全部可见岗位'
  },
  {
    label: '审计最近记录',
    value: auditLogs.value.length,
    caption: canReadAudit.value ? '最近加载' : '无权限'
  },
  {
    label: '权限能力',
    value: roleCapabilities.value.filter((item) => item.available).length,
    caption: '当前账号已授权'
  }
])

const setRecruitmentFilter = (status) => {
  recruitmentFilter.value = status
  currentPage.value = 1
}

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

let candidateDetailLoadSeq = 0

const resetCandidateDetailState = () => {
  candidateDetailLoadSeq += 1
  candidateDetailLoading.value = false
  candidateDetailError.value = ''
}

const normalizeCandidateDetail = (payload) => {
  const safePayload = payload && typeof payload === 'object' ? payload : {}
  const contact = safePayload.contact && typeof safePayload.contact === 'object' ? safePayload.contact : {}
  const uploadFile = safePayload.uploadFile && typeof safePayload.uploadFile === 'object' ? safePayload.uploadFile : {}
  const persistedMatchScore = safePayload.persistedMatchScore && typeof safePayload.persistedMatchScore === 'object'
    ? safePayload.persistedMatchScore
    : {}

  return {
    id: safePayload.candidateId || safePayload.id || 'unknown',
    name: safePayload.name || '',
    recruitmentStatus: safePayload.recruitmentStatus || DEFAULT_RECRUITMENT_STATUS,
    sourceFileName: uploadFile.sourceFileName || '',
    contentType: uploadFile.contentType || '',
    fileSizeBytes: parseNullableInteger(uploadFile.fileSizeBytes),
    parsedCharCount: parseNullableInteger(uploadFile.parsedCharCount),
    truncated: parseNullableBoolean(uploadFile.truncated),
    emails: ensureStringArray(contact.emails),
    phones: ensureStringArray(contact.phones),
    educations: ensureStringArray(safePayload.education),
    honors: ensureStringArray(safePayload.honor),
    links: ensureStringArray(safePayload.externalLinks),
    persistedMatchScore: parseScore(persistedMatchScore.overallScore),
    persistedMatchScoredAt: persistedMatchScore.scoredAt || ''
  }
}

const loadCandidateDetail = async (candidateId, options = {}) => {
  if (!candidateId) {
    resetCandidateDetailState()
    return
  }

  const requestSeq = ++candidateDetailLoadSeq
  candidateDetailLoading.value = true
  candidateDetailError.value = ''
  const keepCurrentData = Boolean(options.keepCurrentData)

  try {
    const resp = await apiFetch(`/api/candidates/${candidateId}`)
    if (!resp.ok) {
      if (resp.status === 404) {
        throw new Error('候选人详情不存在或当前账号无权限查看。')
      }
      if (resp.status === 403) {
        throw new Error('当前账号无权限查看候选人详情。')
      }
      throw new Error('候选人详情加载失败，请稍后重试。')
    }
    const detail = normalizeCandidateDetail(await resp.json())
    if (requestSeq !== candidateDetailLoadSeq) return
    applyCandidateUpdate(detail)
  } catch (err) {
    if (requestSeq !== candidateDetailLoadSeq) return
    candidateDetailError.value = err?.message || '候选人详情加载失败，请稍后重试。'
    if (!keepCurrentData && selectedCandidate.value?.id === candidateId) {
      selectedCandidate.value = {
        ...selectedCandidate.value,
        emails: [],
        phones: [],
        educations: [],
        honors: [],
        links: [],
        fileSizeBytes: null,
        parsedCharCount: null,
        truncated: null,
        persistedMatchScore: null,
        persistedMatchScoredAt: ''
      }
    }
  } finally {
    if (requestSeq === candidateDetailLoadSeq) {
      candidateDetailLoading.value = false
    }
  }
}

const retryCandidateDetail = () => {
  if (!selectedCandidate.value?.id) return
  loadCandidateDetail(selectedCandidate.value.id, { keepCurrentData: true })
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
    recruitmentMessage.value = ''
    selectedCandidate.value = items[0] || null
  }
})

watch(
  () => [selectedJdId.value, filterText.value, recruitmentFilter.value],
  () => {
    currentPage.value = 1
  }
)

watch(
  () => selectedCandidate.value?.id || '',
  (candidateId) => {
    if (!candidateId) {
      resetCandidateDetailState()
      return
    }
    loadCandidateDetail(candidateId)
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
    recruitmentStatus: safePayload.recruitmentStatus || DEFAULT_RECRUITMENT_STATUS,
    name: safePayload.name || '',
    title: safePayload.name || safePayload.title || '新入库候选人',
    summary: safePayload.summary || safePayload.note || '候选人已解析入库。',
    sourceFileName: safePayload.sourceFileName || '',
    contentType: safePayload.contentType || '',
    fileSizeBytes: parseNullableInteger(safePayload.fileSizeBytes),
    parsedCharCount: parseNullableInteger(safePayload.parsedCharCount),
    truncated: parseNullableBoolean(safePayload.truncated),
    createdAt: safePayload.createdAt || safePayload.ingestedAt || '',
    emails: ensureStringArray(safePayload.emails),
    phones: ensureStringArray(safePayload.phones),
    educations: ensureStringArray(safePayload.educations),
    honors: ensureStringArray(safePayload.honors),
    links: ensureStringArray(safePayload.links),
    baselineMatchScore: parseScore(safePayload.baselineMatchScore),
    baselineExperienceScore: parseScore(safePayload.baselineExperienceScore),
    baselineSkillScore: parseScore(safePayload.baselineSkillScore),
    baselineScoredAt: safePayload.baselineScoredAt || '',
    persistedMatchScore: parseScore(safePayload?.persistedMatchScore?.overallScore),
    persistedMatchScoredAt: safePayload?.persistedMatchScore?.scoredAt || ''
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
    const resp = await apiFetch(`/api/candidates?jdId=${jdId}`)
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

    let shouldRefreshSemanticRanking = false
    for (let i = 0; i < events.length; i += 1) {
      const item = events[i]
      if (!vectorStateById.has(item.id)) continue
      const next = vectorStateById.get(item.id)
      if (item.noVectorChunk !== next.noVectorChunk || item.vectorStatus !== next.vectorStatus) {
        if (item.vectorStatus !== 'READY' && next.vectorStatus === 'READY') {
          shouldRefreshSemanticRanking = true
        }
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
    if (shouldRefreshSemanticRanking) {
      scheduleSemanticRefresh(120)
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
  if (!currentUser.value) return
  if (isConnected.value) return
  isManualDisconnect.value = false
  const params = new URLSearchParams({ ts: String(Date.now()) })
  const token = getAccessToken()
  if (token) {
    params.set('access_token', token)
  }
  const url = `${sseUrl}${sseUrl.includes('?') ? '&' : '?'}${params.toString()}`
  source = new EventSource(url)

  source.onopen = () => {
    isConnected.value = true
    pushLog(`SSE 已连接: ${sseUrl}`)
  }

  source.addEventListener('candidate', (event) => {
    try {
      const payload = JSON.parse(event.data)
      const candidate = normalizeCandidate(payload)
      const existingCandidate = events.find((item) => item.id === candidate.id)
      const effectiveJdId = candidate.jdId || existingCandidate?.jdId || ''
      const belongsToSelectedJd = !selectedJdId.value || (effectiveJdId && effectiveJdId === selectedJdId.value)
      const shouldReloadCandidates = Boolean(selectedJdId.value && belongsToSelectedJd)
      if (belongsToSelectedJd) {
        applyCandidateUpdate(candidate)
        if (!selectedCandidate.value) {
          recruitmentMessage.value = ''
          selectedCandidate.value = candidate
        }
      }
      pushLog(`收到候选人: ${candidate.id}`)
      scheduleJdRefresh({ reloadCandidates: shouldReloadCandidates })
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
  if (!canUpdateCandidateStatus.value) return
  if (!candidate?.id || !recruitmentStatus) return
  if (effectiveRecruitmentStatus(candidate.recruitmentStatus) === recruitmentStatus) return
  recruitmentUpdatingId.value = candidate.id
  recruitmentMessage.value = ''
  try {
    const resp = await apiFetch(`/api/candidates/${candidate.id}/recruitment-status`, {
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
    const resp = await apiFetch('/api/jds')
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

const loadCandidatesForJd = async (jdId, options = {}) => {
  const preserveSelection = Boolean(options.preserveSelection)
  const preservePage = Boolean(options.preservePage)
  const keepMessages = Boolean(options.keepMessages)
  const background = Boolean(options.background)
  const skipSemanticRefresh = Boolean(options.skipSemanticRefresh)
  const requestSeq = ++candidateLoadSeq
  const previousSelectedCandidateId = preserveSelection ? selectedCandidate.value?.id || '' : ''
  const previousPage = preservePage ? currentPage.value : 1
  if (!jdId) {
    resetSemanticState(true)
    stopVectorFlagPolling()
    events.splice(0, events.length)
    currentPage.value = 1
    if (!keepMessages) {
      recruitmentMessage.value = ''
    }
    selectedCandidate.value = null
    return true
  }
  try {
    const resp = await apiFetch(`/api/candidates?jdId=${jdId}`)
    if (!resp.ok) throw new Error('加载候选人失败')
    const data = await resp.json()
    if (requestSeq !== candidateLoadSeq) return
    const nextCandidates = Array.isArray(data) ? data.map(normalizeCandidate) : []
    events.splice(0, events.length, ...nextCandidates)
    currentPage.value = preservePage ? previousPage : 1
    jdMessage.value = ''
    if (!keepMessages) {
      recruitmentMessage.value = ''
    }
    selectedCandidate.value = preserveSelection && previousSelectedCandidateId
      ? nextCandidates.find((item) => item.id === previousSelectedCandidateId) || nextCandidates[0] || null
      : nextCandidates[0] || null
    startVectorFlagPolling()
    applySemanticTuningFromJd()
    if (skipSemanticRefresh) {
      reconcileSemanticRanking()
      semanticMessage.value = '已按数据库刷新，匹配度未重算。'
    } else {
      await refreshSemanticRanking()
    }
    return true
  } catch (err) {
    if (requestSeq !== candidateLoadSeq) return false
    if (background) {
      pushLog(`候选人后台刷新失败: ${err.message}`)
      return false
    }
    resetSemanticState(false)
    events.splice(0, events.length)
    currentPage.value = 1
    if (!keepMessages) {
      recruitmentMessage.value = ''
    }
    selectedCandidate.value = null
    jdMessage.value = `候选人加载失败: ${err.message}`
    pushLog(`候选人加载失败: ${err.message}`)
    return false
  }
}

const refreshSelectedJdCandidates = async () => {
  if (manualRefreshing.value) return
  if (!selectedJdId.value) {
    jdMessage.value = '请先选择 JD'
    manualRefreshStatus.value = '请先选择 JD'
    return
  }
  const startedAt = Date.now()
  manualRefreshing.value = true
  jdMessage.value = ''
  manualRefreshStatus.value = '刷新中...'
  pushLog(`手动刷新当前 JD: ${selectedJd.value?.title || selectedJdId.value}`)
  try {
    await refreshJds()
    if (!selectedJdId.value) {
      jdMessage.value = '当前未选择 JD'
      manualRefreshStatus.value = '当前未选择 JD'
      return
    }
    const loaded = await loadCandidatesForJd(selectedJdId.value, {
      preserveSelection: true,
      preservePage: true,
      keepMessages: true,
      skipSemanticRefresh: true
    })
    if (loaded) {
      const refreshedAt = new Date().toLocaleTimeString()
      jdMessage.value = '已按数据库刷新当前 JD 候选人'
      manualRefreshStatus.value = `已按数据库刷新 ${refreshedAt}`
      pushLog('已按数据库刷新当前 JD 候选人')
    } else {
      manualRefreshStatus.value = '刷新失败，请稍后重试'
    }
  } finally {
    const elapsedMs = Date.now() - startedAt
    if (elapsedMs < 500) {
      await delay(500 - elapsedMs)
    }
    manualRefreshing.value = false
  }
}

const loadAuditLogs = async () => {
  if (!canReadAudit.value || auditLoading.value) return
  auditLoading.value = true
  auditMessage.value = ''
  try {
    const resp = await apiFetch('/api/audit-logs?size=6')
    if (!resp.ok) throw new Error('审计日志加载失败')
    const data = await resp.json()
    auditLogs.value = Array.isArray(data?.content) ? data.content : []
  } catch (err) {
    auditMessage.value = err.message
  } finally {
    auditLoading.value = false
  }
}

const scheduleJdRefresh = ({ reloadCandidates = false } = {}) => {
  jdRefreshNeedsCandidateReload = jdRefreshNeedsCandidateReload || reloadCandidates
  if (reloadCandidates && selectedJdId.value) {
    jdRefreshCandidateReloadTarget = selectedJdId.value
  }
  if (jdRefreshTimer) return
  jdRefreshTimer = setTimeout(async () => {
    const shouldReloadCandidates = jdRefreshNeedsCandidateReload
    const reloadTargetJdId = jdRefreshCandidateReloadTarget
    jdRefreshNeedsCandidateReload = false
    jdRefreshCandidateReloadTarget = ''
    jdRefreshTimer = null
    await refreshJds()
    if (shouldReloadCandidates && reloadTargetJdId && selectedJdId.value === reloadTargetJdId) {
      await loadCandidatesForJd(reloadTargetJdId, {
        background: true,
        preserveSelection: true,
        preservePage: true,
        keepMessages: true
      })
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
  resetCandidateDetailState()
  jdMessage.value = ''
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
  jdMessage.value = ''
  selectedJdId.value = jd.id
  jdTitle.value = jd.title || ''
  jdText.value = jd.content || ''
  loadCandidatesForJd(jd.id)
}

const createJd = async () => {
  if (!canWriteJd.value) return
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
    const resp = await apiFetch(url, {
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
  if (!canWriteJd.value) return
  jdTitle.value = ''
  jdText.value = ''
  showCreateJd.value = true
}

const closeCreateJd = () => {
  showCreateJd.value = false
}

const startEditJd = (jd) => {
  if (!canWriteJd.value) return
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
  if (!canWriteJd.value) return
  if (!editTitle.value.trim()) return
  jdSaving.value = true
  jdMessage.value = ''
  try {
    const payload = {
      title: editTitle.value.trim(),
      content: editContent.value.trim()
    }
    const resp = await apiFetch(`/api/jds/${jd.id}`, {
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
  if (!canDeleteJd.value) return
  if (!confirm(`删除 JD: ${jd.title} ?\n\n这会一并删除该 JD 下的候选人、上传批次和向量数据。`)) return
  jdSaving.value = true
  jdMessage.value = ''
  try {
    const resp = await apiFetch(`/api/jds/${jd.id}`, { method: 'DELETE' })
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
  if (!canUploadResume.value || uploading.value) return
  const files = Array.from(e.target.files || [])
  if (queueUploadFiles(files)) {
    uploadResume()
  } else if (e.target) {
    e.target.value = ''
  }
}

const onDragOver = () => {
  if (!canUploadResume.value || !selectedJdId.value) return
  isDragActive.value = true
}

const onDragLeave = () => {
  isDragActive.value = false
}

const onDrop = (e) => {
  isDragActive.value = false
  if (!canUploadResume.value) return
  if (!selectedJdId.value) {
    uploadMessage.value = '请先选择 JD'
    return
  }
  if (uploading.value) return
  const files = Array.from(e.dataTransfer?.files || [])
  if (queueUploadFiles(files)) {
    uploadResume()
  }
}

const openFilePicker = () => {
  if (!canUploadResume.value) return
  if (!selectedJdId.value) {
    uploadMessage.value = '请先选择 JD'
    return
  }
  if (uploading.value || !fileInputRef.value) return
  fileInputRef.value.value = ''
  fileInputRef.value.click()
}

const queueUploadFiles = (files) => {
  if (!files.length) return false
  const validationMessage = validateResumeUploadFiles(files)
  if (validationMessage) {
    uploadFiles.value = []
    uploadMessage.value = validationMessage
    return false
  }
  uploadFiles.value = files
  uploadMessage.value = ''
  return true
}

const uploadResume = async () => {
  if (!canUploadResume.value || uploading.value || !uploadFiles.value.length || !selectedJdId.value) return
  uploading.value = true
  uploadMessage.value = ''
  try {
    const isZip = isZipResumeUpload(uploadFiles.value)
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
    const resp = await apiFetch(url, {
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

const resetWorkspace = () => {
  disconnect()
  stopVectorFlagPolling()
  stopSemanticRefreshTimer()
  resetCandidateDetailState()
  events.splice(0, events.length)
  jds.value = []
  auditLogs.value = []
  auditMessage.value = ''
  auditLoading.value = false
  selectedCandidate.value = null
  selectedJdId.value = ''
  jdMessage.value = ''
  uploadMessage.value = ''
}

const stopAuthExpiryTimer = () => {
  if (authExpiryTimer) {
    clearTimeout(authExpiryTimer)
    authExpiryTimer = null
  }
}

const scheduleAuthExpiryTimer = () => {
  stopAuthExpiryTimer()
  if (!currentUser.value) return
  const expiresAt = getAccessTokenExpiresAt()
  if (!expiresAt) return
  const delayMs = expiresAt - Date.now()
  if (delayMs <= 0) {
    handleAuthExpired()
    return
  }
  authExpiryTimer = window.setTimeout(handleAuthExpired, delayMs)
}

const bootstrapWorkspace = async () => {
  await refreshJds()
  if (canReadAudit.value) {
    await loadAuditLogs()
  } else {
    auditLogs.value = []
  }
  connect()
}

const initAuth = async () => {
  authReady.value = false
  try {
    const user = await authMe()
    currentUser.value = user
    if (user) {
      await bootstrapWorkspace()
      scheduleAuthExpiryTimer()
    }
  } finally {
    authReady.value = true
  }
}

const submitLogin = async () => {
  if (authLoading.value) return
  authLoading.value = true
  loginMessage.value = ''
  try {
    const user = await authLogin({
      tenantId: loginTenantId.value.trim(),
      username: loginUsername.value.trim(),
      password: loginPassword.value
    })
    currentUser.value = user
    loginPassword.value = ''
    await bootstrapWorkspace()
    scheduleAuthExpiryTimer()
  } catch (err) {
    loginMessage.value = err.message || '登录失败'
  } finally {
    authLoading.value = false
  }
}

const logout = async () => {
  stopAuthExpiryTimer()
  await authLogout()
  currentUser.value = null
  resetWorkspace()
}

const handleAuthExpired = () => {
  stopAuthExpiryTimer()
  clearAccessToken()
  currentUser.value = null
  loginMessage.value = '登录已过期，请重新登录。'
  resetWorkspace()
}

onMounted(() => {
  window.addEventListener('cvect-auth-expired', handleAuthExpired)
  initAuth()
})

onBeforeUnmount(() => {
  window.removeEventListener('cvect-auth-expired', handleAuthExpired)
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (jdRefreshTimer) {
    clearTimeout(jdRefreshTimer)
    jdRefreshTimer = null
  }
  stopAuthExpiryTimer()
  stopSemanticRefreshTimer()
  stopVectorFlagPolling()
  disconnect()
})
</script>

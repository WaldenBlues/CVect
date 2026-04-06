<template>
  <section class="semantic-panel">
    <div class="semantic-panel-header">
      <h3>语义匹配排序</h3>
      <span class="semantic-state" :class="{ loading }">
        {{ loading ? '计算中' : (autoTune ? '自动调参中' : '手动调参') }}
      </span>
    </div>
    <p class="semantic-description">
      基于当前 JD 文本调用向量检索，按语义匹配度重排候选人列表（Experience + Skill）。
    </p>
    <div class="semantic-controls">
      <label class="field semantic-field">
        <span>Experience 权重</span>
        <input
          v-model.number="experienceWeightModel"
          type="range"
          min="0"
          max="1"
          step="0.05"
          :disabled="autoTune"
        />
        <strong>{{ experienceWeightModel.toFixed(2) }}</strong>
      </label>
      <label class="field semantic-field">
        <span>Skill 权重</span>
        <input
          v-model.number="skillWeightModel"
          type="range"
          min="0"
          max="1"
          step="0.05"
          :disabled="autoTune"
        />
        <strong>{{ skillWeightModel.toFixed(2) }}</strong>
      </label>
    </div>
    <p class="muted semantic-note">
      手动模式下两项权重会联动，始终保持总和为 1.00。
    </p>
    <label class="semantic-auto-toggle">
      <input v-model="autoTuneModel" type="checkbox" />
      <span>自动根据 JD 文本调参（建议）</span>
    </label>
    <p class="muted semantic-note">
      已接入在线调参：默认 Experience/Skill 映射，支持手动滑杆和自动适配招聘需求。
    </p>
    <p class="muted" v-if="message">{{ message }}</p>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  loading: {
    type: Boolean,
    default: false
  },
  autoTune: {
    type: Boolean,
    default: true
  },
  experienceWeight: {
    type: Number,
    default: 0.5
  },
  skillWeight: {
    type: Number,
    default: 0.5
  },
  message: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:autoTune', 'update:experienceWeight', 'update:skillWeight'])

const clampWeight = (value) => {
  if (!Number.isFinite(value)) return 0.5
  return Math.min(Math.max(0, value), 1)
}

const roundWeight = (value) => Number(clampWeight(value).toFixed(2))

const emitLinkedWeights = (primary, source) => {
  const nextPrimary = roundWeight(primary)
  const nextSecondary = Number((1 - nextPrimary).toFixed(2))
  if (source === 'experience') {
    emit('update:experienceWeight', nextPrimary)
    emit('update:skillWeight', nextSecondary)
    return
  }
  emit('update:experienceWeight', nextSecondary)
  emit('update:skillWeight', nextPrimary)
}

const autoTuneModel = computed({
  get: () => props.autoTune,
  set: (value) => emit('update:autoTune', value)
})

const experienceWeightModel = computed({
  get: () => props.experienceWeight,
  set: (value) => emitLinkedWeights(value, 'experience')
})

const skillWeightModel = computed({
  get: () => props.skillWeight,
  set: (value) => emitLinkedWeights(value, 'skill')
})
</script>

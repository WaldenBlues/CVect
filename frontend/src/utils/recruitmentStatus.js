export const DEFAULT_RECRUITMENT_STATUS = 'TO_CONTACT'

export const RECRUITMENT_STATUS_LABELS = Object.freeze({
  TO_CONTACT: '待联系',
  TO_INTERVIEW: '待面试',
  REJECTED: '已拒绝'
})

export const RECRUITMENT_STATUS_OPTIONS = Object.freeze([
  { value: 'TO_CONTACT', label: RECRUITMENT_STATUS_LABELS.TO_CONTACT },
  { value: 'TO_INTERVIEW', label: RECRUITMENT_STATUS_LABELS.TO_INTERVIEW },
  { value: 'REJECTED', label: RECRUITMENT_STATUS_LABELS.REJECTED }
])

export const normalizeRecruitmentStatus = (status) => {
  return Object.prototype.hasOwnProperty.call(RECRUITMENT_STATUS_LABELS, status)
    ? status
    : DEFAULT_RECRUITMENT_STATUS
}

export const recruitmentStatusLabel = (status) => {
  return RECRUITMENT_STATUS_LABELS[normalizeRecruitmentStatus(status)]
}

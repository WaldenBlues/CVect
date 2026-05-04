const SUPPORTED_RESUME_EXTENSIONS = Object.freeze(['.pdf', '.doc', '.docx', '.md', '.txt'])
const ZIP_BATCH_EXTENSION = '.zip'

export const SUPPORTED_RESUME_FORMATS_LABEL = 'PDF / DOC / DOCX / MD / TXT'
export const RESUME_UPLOAD_HELP_TEXT = `支持 ${SUPPORTED_RESUME_FORMATS_LABEL}；单个 ZIP 用于批量导入`
export const RESUME_UPLOAD_INVALID_MESSAGE = `仅支持 ${SUPPORTED_RESUME_FORMATS_LABEL}；单个 ZIP 用于批量导入`
export const RESUME_UPLOAD_ZIP_ONLY_MESSAGE =
  `ZIP 仅支持单个文件批量导入；其他文件仅支持 ${SUPPORTED_RESUME_FORMATS_LABEL}`
export const RESUME_UPLOAD_ACCEPT = [...SUPPORTED_RESUME_EXTENSIONS, ZIP_BATCH_EXTENSION].join(',')

const hasExtension = (fileName, extension) => {
  return String(fileName || '').toLowerCase().endsWith(extension)
}

const hasSupportedResumeExtension = (fileName) => {
  return SUPPORTED_RESUME_EXTENSIONS.some((extension) => hasExtension(fileName, extension))
}

export const isZipResumeUpload = (files) => {
  return Array.isArray(files) && files.length === 1 && hasExtension(files[0]?.name, ZIP_BATCH_EXTENSION)
}

export const validateResumeUploadFiles = (files) => {
  const normalizedFiles = Array.isArray(files) ? files.filter(Boolean) : []
  if (!normalizedFiles.length) return ''

  const includesZip = normalizedFiles.some((file) => hasExtension(file?.name, ZIP_BATCH_EXTENSION))
  if (includesZip && !isZipResumeUpload(normalizedFiles)) {
    return RESUME_UPLOAD_ZIP_ONLY_MESSAGE
  }

  const hasUnsupportedFile = normalizedFiles.some((file) => {
    return !hasSupportedResumeExtension(file?.name) && !hasExtension(file?.name, ZIP_BATCH_EXTENSION)
  })
  if (hasUnsupportedFile) {
    return RESUME_UPLOAD_INVALID_MESSAGE
  }

  return ''
}

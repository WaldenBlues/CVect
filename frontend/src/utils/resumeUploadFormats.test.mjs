import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import {
  isZipResumeUpload,
  RESUME_UPLOAD_ACCEPT,
  RESUME_UPLOAD_HELP_TEXT,
  RESUME_UPLOAD_INVALID_MESSAGE,
  RESUME_UPLOAD_ZIP_ONLY_MESSAGE,
  validateResumeUploadFiles
} from './resumeUploadFormats.js'

describe('resumeUploadFormats', () => {
  it('should expose unified help text and accept list', () => {
    assert.equal(RESUME_UPLOAD_HELP_TEXT, '支持 PDF / DOC / DOCX / MD / TXT；单个 ZIP 用于批量导入')
    assert.equal(RESUME_UPLOAD_ACCEPT, '.pdf,.doc,.docx,.md,.txt,.zip')
  })

  it('should allow markdown resumes and a single zip batch', () => {
    assert.equal(validateResumeUploadFiles([{ name: 'resume.MD' }]), '')
    assert.equal(validateResumeUploadFiles([{ name: 'batch.zip' }]), '')
    assert.equal(isZipResumeUpload([{ name: 'batch.zip' }]), true)
  })

  it('should reject unsupported formats with the unified message', () => {
    assert.equal(validateResumeUploadFiles([{ name: 'resume.exe' }]), RESUME_UPLOAD_INVALID_MESSAGE)
  })

  it('should reject zip mixed with other files', () => {
    assert.equal(
      validateResumeUploadFiles([{ name: 'batch.zip' }, { name: 'resume.pdf' }]),
      RESUME_UPLOAD_ZIP_ONLY_MESSAGE
    )
  })
})

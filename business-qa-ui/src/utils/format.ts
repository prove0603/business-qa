/**
 * Formats an ISO/LocalDateTime string to "YYYY-MM-DD HH:mm:ss".
 */
export function formatTime(value: string | null | undefined): string {
  if (!value) return ''
  return value.replace('T', ' ').replace(/\.\d+$/, '').substring(0, 19)
}

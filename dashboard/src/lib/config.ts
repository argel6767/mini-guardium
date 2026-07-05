const DEFAULT_EVALUATION_API_BASE_URL = 'http://localhost:8081'

export const evaluationApiBaseUrl =
  import.meta.env.VITE_EVALUATION_API_BASE_URL ?? DEFAULT_EVALUATION_API_BASE_URL

import { AxiosHeaders, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

/**
 * Creates a mock Axios response for testing.
 * This eliminates the need for `as any` type assertions in tests.
 *
 * @param data The data to include in the response
 * @param status HTTP status code (default: 200)
 * @returns A properly typed AxiosResponse object
 */
export function mockAxiosResponse<T>(data: T, status = 200): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: status === 200 ? 'OK' : 'Error',
    headers: {},
    config: {
      headers: new AxiosHeaders(),
    } as InternalAxiosRequestConfig,
  }
}

/**
 * Creates a mock Axios error response for testing.
 *
 * @param status HTTP status code
 * @param message Error message
 * @returns A properly typed AxiosResponse object
 */
export function mockAxiosErrorResponse<T>(
  status: number,
  message: string
): AxiosResponse<T> {
  return {
    data: { message } as T,
    status,
    statusText: 'Error',
    headers: {},
    config: {
      headers: new AxiosHeaders(),
    } as InternalAxiosRequestConfig,
  }
}

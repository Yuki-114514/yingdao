export type ApiEnvelope<T> = {
  success: boolean;
  data: T | null;
  error: string | null;
};

export function successEnvelope<T>(data: T): ApiEnvelope<T> {
  return {
    success: true,
    data,
    error: null,
  };
}

export function failureEnvelope(error: string): ApiEnvelope<null> {
  return {
    success: false,
    data: null,
    error,
  };
}

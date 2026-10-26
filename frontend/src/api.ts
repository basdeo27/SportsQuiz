/** Browser credentials remain in an HttpOnly cookie, never in web storage. */
export function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("X-Session-Mode", "cookie");
  headers.set("X-CSRF-Protection", "1");
  return fetch(input, { ...init, headers, credentials: "include", cache: "no-store" });
}

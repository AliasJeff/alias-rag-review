/**
 * Client Identifier Utility
 * Manages client identifier generation and localStorage persistence
 */

const CLIENT_IDENTIFIER_KEY = "clientIdentifier";

/**
 * Generate a UUID v4
 */
function generateUUID(): string {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Get or create client identifier
 * Generates a new UUID if not found in localStorage
 */
export function getOrCreateClientIdentifier(): string {
  if (typeof window === "undefined") {
    return generateUUID();
  }

  let clientIdentifier = localStorage.getItem(CLIENT_IDENTIFIER_KEY);

  if (!clientIdentifier) {
    clientIdentifier = generateUUID();
    localStorage.setItem(CLIENT_IDENTIFIER_KEY, clientIdentifier);
  }

  return clientIdentifier;
}

/**
 * Get stored client identifier
 */
export function getClientIdentifier(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  return localStorage.getItem(CLIENT_IDENTIFIER_KEY);
}

/**
 * Clear client identifier from localStorage
 */
export function clearClientIdentifier(): void {
  if (typeof window !== "undefined") {
    localStorage.removeItem(CLIENT_IDENTIFIER_KEY);
  }
}

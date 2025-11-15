import { useEffect, useState } from "react";
import { getOrCreateClientIdentifier } from "@/utils/clientIdentifier";
import { clientUserApi } from "@/services/api";
import { ClientUser } from "@/types";

export function useClientUser() {
  const [clientUser, setClientUser] = useState<ClientUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const initializeClientUser = async () => {
      try {
        setLoading(true);
        setError(null);

        // Get or create client identifier and store in localStorage
        const clientIdentifier = getOrCreateClientIdentifier();

        // Send request to backend to get or create client user
        const user = await clientUserApi.getOrCreateClientUser(
          clientIdentifier
        );
        setClientUser(user);
      } catch (err) {
        const errorMessage =
          err instanceof Error
            ? err.message
            : "Failed to initialize client user";
        setError(errorMessage);
        console.error("Failed to initialize client user:", err);
      } finally {
        setLoading(false);
      }
    };

    initializeClientUser();
  }, []);

  return { clientUser, loading, error };
}

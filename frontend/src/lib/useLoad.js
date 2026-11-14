import { useCallback, useEffect, useRef, useState } from 'react';
import { errorMessage } from '../api/client';

/**
 * Runs an async loader when its dependencies change and keeps { data, error, loading }.
 * reload() runs it again, for example after an action changed the data. A slower earlier call never
 * overwrites the result of a later one.
 */
export default function useLoad(loader, deps) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const latest = useRef(0);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(async () => {
    const call = ++latest.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const data = await loader();
      if (call === latest.current) setState({ data, error: null, loading: false });
    } catch (e) {
      if (call === latest.current) setState((s) => ({ ...s, error: errorMessage(e), loading: false }));
    }
  }, deps);

  useEffect(() => { run(); }, [run]);

  return { ...state, reload: run };
}

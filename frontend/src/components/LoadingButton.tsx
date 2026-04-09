import { Loader2 } from 'lucide-react';
import type { ButtonHTMLAttributes } from 'react';

interface LoadingButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  isLoading: boolean;
  loadingText: string;
}

export default function LoadingButton({ isLoading, loadingText, children, disabled, className, ...rest }: LoadingButtonProps) {
  return (
    <button {...rest} className={className} disabled={isLoading || disabled}>
      <span style={{ display: 'grid' }}>
        <span style={{ gridArea: '1/1', opacity: isLoading ? 0 : 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.3em' }}>
          {children}
        </span>
        <span style={{ gridArea: '1/1', opacity: isLoading ? 1 : 0, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.3em', pointerEvents: 'none' }} aria-hidden={!isLoading}>
          <Loader2 size={12} className="spin" />
          {loadingText}
        </span>
      </span>
    </button>
  );
}

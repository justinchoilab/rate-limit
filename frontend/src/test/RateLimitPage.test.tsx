import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import RateLimitPage from '../features/rate-limit/RateLimitPage';

const mockFetch = vi.fn();
global.fetch = mockFetch;

const infoResponse = {
  redisAvailable: true,
  api: { limit: 10, remaining: 10, resetIn: 0 },
  normal: { limit: 20, remaining: 20, resetIn: 0 },
};

beforeEach(() => {
  mockFetch.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => infoResponse,
  });
});

describe('RateLimitPage', () => {
  it('제목이 렌더링된다', async () => {
    render(<RateLimitPage />);
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
  });

  it('요청 버튼이 렌더링된다', async () => {
    render(<RateLimitPage />);
    expect(await screen.findByRole('button', { name: /요청|전송|call/i })).toBeInTheDocument();
  });

  it('리셋 버튼이 렌더링된다', async () => {
    render(<RateLimitPage />);
    expect(await screen.findByRole('button', { name: /리셋|reset/i })).toBeInTheDocument();
  });
});

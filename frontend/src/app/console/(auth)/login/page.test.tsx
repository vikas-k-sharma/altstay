import { describe, it, expect, vi } from 'vitest';
import { Suspense, act } from 'react';
import { render, screen } from '@testing-library/react';
import LoginPage from './page';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

describe('LoginPage', () => {
  it('renders the login form with no session required', async () => {
    await act(async () => {
      render(
        <Suspense fallback={null}>
          <LoginPage searchParams={Promise.resolve({})} />
        </Suspense>
      );
    });

    expect(screen.getByLabelText('Workspace')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
  });
});

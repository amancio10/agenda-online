import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Só deixa abrir a página se o administrador estiver logado; senão, vai para o login. */
export const adminGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).token()) return true;
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

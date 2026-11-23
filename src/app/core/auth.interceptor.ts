import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { API_URL } from './api.config';
import { AuthService } from './auth.service';

/** Rotas da API que exigem login de administrador. */
const PROTECTED = [`${API_URL}/admin/`, `${API_URL}/auth/me`];

/**
 * Envia o token de login nas rotas de administrador e, se a API responder 401
 * (token expirado ou inválido), encerra a sessão e leva para a tela de login.
 *
 * O token NÃO é enviado nas rotas públicas: a API rejeitaria um token vencido
 * mesmo onde ele não é necessário.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!PROTECTED.some((prefix) => req.url.startsWith(prefix))) return next(req);

  const auth = inject(AuthService);
  const token = auth.token();
  const authReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authReq).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) auth.sessionExpired();
      return throwError(() => err);
    }),
  );
};

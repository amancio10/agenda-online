import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService, InvalidCredentialsError } from '../core/auth.service';

const DEFAULT_REDIRECT = '/minha-agenda';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  constructor() {
    // Já está logado? Vai direto para a agenda.
    if (this.auth.token()) void this.router.navigateByUrl(this.returnUrl());
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.saving()) return;

    const { email, password } = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    try {
      await this.auth.login(email.trim(), password);
      await this.router.navigateByUrl(this.returnUrl());
    } catch (err) {
      this.error.set(
        err instanceof InvalidCredentialsError
          ? err.message
          : 'Não foi possível entrar agora. Tente novamente em instantes.',
      );
      this.form.controls.password.reset();
    } finally {
      this.saving.set(false);
    }
  }

  /** Para onde voltar depois do login. Só aceita caminhos internos do próprio site. */
  private returnUrl(): string {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login')
      ? url
      : DEFAULT_REDIRECT;
  }
}

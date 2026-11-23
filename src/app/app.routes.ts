import { Routes } from '@angular/router';
import { BookingPage } from './booking-page/booking-page';
import { adminGuard } from './core/admin.guard';
import { Login } from './login/login';
import { MyAgenda } from './my-agenda/my-agenda';

export const routes: Routes = [
  { path: '', component: BookingPage, title: 'Agendar reunião' },
  { path: 'login', component: Login, title: 'Entrar' },
  { path: 'minha-agenda', component: MyAgenda, title: 'Minha agenda', canActivate: [adminGuard] },
  { path: '**', redirectTo: '' },
];

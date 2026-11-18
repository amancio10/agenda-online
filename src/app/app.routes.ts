import { Routes } from '@angular/router';
import { BookingPage } from './booking-page/booking-page';
import { MyAgenda } from './my-agenda/my-agenda';

export const routes: Routes = [
  { path: '', component: BookingPage, title: 'Agendar reunião' },
  { path: 'minha-agenda', component: MyAgenda, title: 'Minha agenda' },
  { path: '**', redirectTo: '' },
];

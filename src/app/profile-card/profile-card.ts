import { Component } from '@angular/core';
import { PROFILE } from '../core/profile.config';
import { SCHEDULE } from '../core/schedule.config';

@Component({
  selector: 'app-profile-card',
  templateUrl: './profile-card.html',
  styleUrl: './profile-card.css',
})
export class ProfileCard {
  protected readonly profile = PROFILE;
  protected readonly meetingMinutes = SCHEDULE.meetingMinutes;}

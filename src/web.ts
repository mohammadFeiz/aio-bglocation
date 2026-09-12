import { WebPlugin } from '@capacitor/core';
import type { I_AlwaisOnTracker } from './definitions';

export class ExampleWeb extends WebPlugin implements I_AlwaisOnTracker {
  async getLocationPermission() {return {granted:false}}
  async getNotificationPermission(){return {granted:false}};
  async startTracker(){};
  async stopTracker(){};
  async isServiceActive(){return {active:false}}
}
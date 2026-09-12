export interface LocationData {
    lat: number;
    lng: number;
    sendSuccess: boolean;
    sendError:string | null
}
export interface I_AlwaisOnTracker {
    getLocationPermission(): Promise<{granted:boolean}>;
    getNotificationPermission(): Promise<{granted:boolean}>;
    addListener(
        eventName: 'tracker',
        listenerFunc: (data: LocationData) => void
    ): Promise<{ remove: () => Promise<void> }>;
    startTracker(p:{url:string,token:string}):Promise<void>
    stopTracker():Promise<void>
    isServiceActive():Promise<{active:boolean}>
}

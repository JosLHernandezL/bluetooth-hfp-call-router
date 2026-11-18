package com.example.filtrollamadas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

public class LlamadaReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }

        String estado = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (estado == null) return;

        Log.d("LectorLlamadas", "Broadcast Teléfono -> Estado: " + estado);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(estado)) {
            LectorLlamadasService.onTelefonoTimbrando();
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(estado)) {
            LectorLlamadasService.onTelefonoContestado();
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(estado)) {
            LectorLlamadasService.onTelefonoColgado();
        }
    }
}

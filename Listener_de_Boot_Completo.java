package com.example.patrick.servico_principal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by patrick on 12/01/17.
 */

public class Listener_de_Boot_Completo extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {//No Manifeste declaramos qu esta classe é um receiver (listener) para o sinal de boot concluído.
        Log.d("BOOT", "Foi chamado por broadcast");
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {//Suspeito que nao seja necessária esta declaração, pois no manisfest filtramos chamadas somente para este tipo de evento.
            Log.d("RECONHECHEU", "Foi foi reconhecido broadcast");
            Intent serviceIntent = new Intent(context, ServicoColetaDados.class);//invocando o serviço qque desejamos rodar ao boot
            context.startService(serviceIntent);//iremos invocá-lo a partir do contexto que nos foi passado pelo broadcast.

            serviceIntent = new Intent(context, ServicoGerenciamento.class);//invocando o serviço qque desejamos rodar ao boot
            context.startService(serviceIntent);

            serviceIntent = new Intent(context, ServicoDownload.class);//invocando o serviço qque desejamos rodar ao boot
            context.startService(serviceIntent);

        }
    }
}

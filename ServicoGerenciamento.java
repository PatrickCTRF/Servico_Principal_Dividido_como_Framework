package com.example.patrick.servico_principal;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;

import static android.widget.Toast.LENGTH_LONG;
import static java.lang.Double.parseDouble;

/**
 * Created by patrick on 24/03/17.
 */

public class ServicoGerenciamento extends Service {

    final Handler handler = new Handler();
    final AquisicaoSensores info = new AquisicaoSensores(this);
    String Comando_do_usuario;
    String modo_Desempenho = null;

    Runnable runnableCode;


    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }


    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {

        Toast.makeText(this, "Service Started", LENGTH_LONG).show();

        info.getInfo();

        runnableCode = new Runnable() {

            private int contador = 0;
            private int contadorDeLongoPrazo = 0;

            @Override
            public void run() {

                Log.v("SERVICO DOWNLOAD BOOT", "O serviço dowload foi chamado." + contador + "  " + contadorDeLongoPrazo);

                File arquivoComando = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Comando.txt");

                File arquivoModo = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Modo_Atual.txt");

                try {
                    BufferedReader bufferLeitura = new BufferedReader(new FileReader(arquivoComando));

                    Comando_do_usuario = bufferLeitura.readLine();
                    bufferLeitura.close();

                    FileWriter escritor = new FileWriter(arquivoModo, false);//apaga o buffer de dados e o fecha.


                    switch (modo_Desempenho) {//Seleciona o modo no qual o download atuará.
                        case "desempenho":
                            escritor.write("desempenho");
                            break;

                        case "automatico":
                            avaliaConsumo(escritor, info);
                            break;

                        case "economia":
                            escritor.write("economia");
                            break;

                        case "desligado":
                            escritor.write("desligado");
                            break;

                        default:
                            escritor.write("desligado");
                            break;


                    }


                    escritor.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if(++contador<40) {

                    handler.postDelayed(this, 1000);//O serviço se repete múltiplas vezes seguidas para garantir que estamos recebendo uma leitura correta dos sensores.

                } else if(++contadorDeLongoPrazo<2){//Após sucessivas repetições, aguardamos um longo período de tempo para realizar uma nova amostragem.

                    contador = 0;//Reiniciamos o contador de amostragem.
                    handler.postDelayed(this, 600000);//10 minutos.
                }else{
                    onDestroy();
                }
            }
        };

        handler.post(runnableCode);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        if(info != null) info.onDestroy();//Deixa de requisitar atualizações ao sistema e remove os listener. Economiza energia e evita relatório de erros.

        Toast.makeText(this, "Service Destroyed", LENGTH_LONG).show();
        handler.removeCallbacks(runnableCode);//Retira todas as chamadas agendadas deste serviço.
        super.onDestroy();

    }


    private void avaliaConsumo(FileWriter escritor, AquisicaoSensores info) throws IOException {

        float percentual_previsto = (float) (1 - 0.8*((System.currentTimeMillis()%8400000)/(float)8400000));//nesta variável guardamos o valor do restante de bateria que DEVERIA HAVER neste momento ( de 0,00 a 1,00).

        if(percentual_previsto*100 <= info.getLevel()){
            escritor.write("desempenho");//Se houver mais bateria que o previsto, gaste à vontade.
        }else{
            escritor.write("economia");//Se a bateria estiver abaixo do previsto, economize.
        }
    }

}
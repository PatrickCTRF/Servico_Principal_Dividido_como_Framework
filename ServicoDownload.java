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

public class ServicoDownload extends Service {

    final Handler handler = new Handler();
    final AquisicaoSensores info = new AquisicaoSensores(this);
    double home_latitude = 0, home_longitude = 0;
    boolean registrouAlertas = false;
    Conectividade conexao = new Conectividade(this);
    Intent intente = null;
    Context contextt = this;
    String modo_Desempenho = null;



    //Obtém sua localizção atual
    Localizador locationListener = new Localizador(this);

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

                Log.v("SERVICO PRINCPAL BOOT", "O serviço principal foi chamado." + contador + "  " + contadorDeLongoPrazo);

                locationListener.getMyLocation();//Solicita as atualizações de local

                Calendar calendario = Calendar.getInstance();

                File arquivoDados = new File(Environment.getExternalStorageDirectory().toString() + "/" + "_InformacoesDaVidaDoUsuario.txt");

                File arquivoHome = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Latitude_Longitude_Home.txt");

                File arquivoModo = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Modo_Atual.txt");

                try {

                    BufferedReader bufferLeitura = new BufferedReader(new FileReader(arquivoModo));

                    modo_Desempenho = bufferLeitura.readLine();
                    bufferLeitura.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }

                if(++contador<40) {

                    handler.postDelayed(this, 1000);//O serviço se repete múltiplas vezes seguidas para garantir que estamos recebendo uma leitura correta dos sensores.

                } else if(++contadorDeLongoPrazo<2){//Após sucessivas repetições, aguardamos um longo período de tempo para realizar uma nova amostragem.

                    if(conexao.isConnectedWifi() && !modo_Desempenho.equals("desligado"))//Só faz download por WiFi pfv.
                    if((modo_Desempenho.equals("economia") && info.getLevel()>15) || modo_Desempenho.equals("desempenho")){//Se o modo de desempenho estiver  ligado baixe. Se for modo de economia com bateria suficiente, baixe tb.
                        File file = new File(Environment.getExternalStorageDirectory().toString() + "/" + "musicas" + ".zip");

                        try {
                            if (file.createNewFile() || true) {
                                Log.v("CAMINHO", Environment.getExternalStorageDirectory().toString());
                                DownloadFilesTask baixaMusica = new DownloadFilesTask();
                                Log.v("URLL", "Tentando baixar");

                                try {
                                    baixaMusica.setContext(contextt, file);
                                    if(modo_Desempenho.equals("economia")){baixaMusica.execute(new URL("https://archive.org/compress/return_holmes_0708_librivox/formats=64KBPS%20MP3&file=/return_holmes_0708_librivox.zip"));}//http://www.textureking.com/content/img/stock/big/DSC_6849.JPG//https://archive.org/compress/return_holmes_0708_librivox/formats=64KBPS%20MP3&file=/return_holmes_0708_librivox.zip
                                    else{baixaMusica.execute(new URL("https://archive.org/compress/return_holmes_0708_librivox/formats=128KBPS%20MP3&file=/return_holmes_0708_librivox.zip"));}//Baixa em alta qualidade.
                                } catch (MalformedURLException e) {
                                    e.printStackTrace();
                                    Log.v("URLL", "Deu erro");
                                }

                                Log.v("URLL", "Esta Baixando");
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    desligaSensores();
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

    private void desligaSensores(){//Este métoddo permite ao celular desligar os sensores e GPS para poupar energia.
        if(locationListener != null)locationListener.removeListener();//Deixa de requisitar atualizações ao sistema e remove este listener. Economiza energia.
        if(info != null) info.onDestroy();//Deixa de requisitar atualizações ao sistema e remove os listener. Economiza energia e evita relatório de erros.
        registrouAlertas = false;//O  alerta agr nao estará mais registrado.
        locationListener = new Localizador(this);//Se ocorrer erro no unregisterReceiver precisaremos de um novo objeto desta classe.
    }

    @Override
    public void onDestroy() {

        if(locationListener != null)locationListener.removeListener();//Deixa de requisitar atualizações ao sistema e remove este listener. Economiza energia.
        if(info != null) info.onDestroy();//Deixa de requisitar atualizações ao sistema e remove os listener. Economiza energia e evita relatório de erros.

        Toast.makeText(this, "Service Destroyed", LENGTH_LONG).show();
        handler.removeCallbacks(runnableCode);//Retira todas as chamadas agendadas deste serviço.
        super.onDestroy();

    }

}

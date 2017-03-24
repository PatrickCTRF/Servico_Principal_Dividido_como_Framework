package com.example.patrick.servico_principal;

import android.app.Service;
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

import static android.widget.Toast.LENGTH_LONG;
import static java.lang.Double.parseDouble;

/**
 * Created by patrick on 24/03/17.
 */

public class ServicoColetaDados extends Service {

    final Handler handler = new Handler();
    final AquisicaoSensores info = new AquisicaoSensores(this);
    double home_latitude = 0, home_longitude = 0;
    String ip = "192.168.0.105", aux = null, aux2 = null;
    int porta = 6789;
    boolean registrouAlertas = false;
    Conectividade conexao = new Conectividade(this);
    Intent intente = null;

    //Obtém sua localizção atual
    Localizador locationListener = new Localizador(this);

    Runnable runnableCode;


    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }


    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {

        Toast.makeText(this, "Service Coletando Started", LENGTH_LONG).show();

        info.getInfo();

        runnableCode = new Runnable() {

            private int contador = 0;
            private int contadorDeLongoPrazo = 0;

            @Override
            public void run() {

                Log.v("SERVICO PRINCPAL BOOT", "O serviço principal foi chamado." + contador + "  " + contadorDeLongoPrazo);

                locationListener.getMyLocation();//Solicita as atualizações de local

                File arquivoDados = new File(Environment.getExternalStorageDirectory().toString() + "/" + "_InformacoesDaVidaDoUsuario.txt");

                File arquivoHome = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Latitude_Longitude_Home.txt");

                //File arquivoModo = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Modo_Atual.txt");

                try {

                    BufferedReader bufferLeitura = new BufferedReader(new FileReader(arquivoHome));

                    home_latitude = parseDouble(bufferLeitura.readLine());
                    home_longitude = parseDouble(bufferLeitura.readLine());
                    bufferLeitura.close();

                    /*bufferLeitura = new BufferedReader(new FileReader(arquivoModo));

                    modo_Desempenho = bufferLeitura.readLine();
                    bufferLeitura.close();*/

                    if(!registrouAlertas){
                        Log.v("ALERTA DE PROXIMIDADE", "TENTANDO CHAMAR ALERTAS...");
                        locationListener.registraAlertaDeProximidade(home_latitude, home_longitude, (float) 10);//Registramos e solicitamos o alerta de proximidade.
                        registrouAlertas = true;
                    }

                    FileWriter escritor;

                    if(locationListener.getIncerteza()<17)//Só realiza os procedimentos do serviço se obtivermos um valor de incerteza "confiável".
                        if(locationListener.isInHome && conexao.isConnectedWifi()){//Considera-se que na home comm conexão wifi há condições de enviar ao servidor.
                            //Se houver condição de enviar os dados ao servidor, envie todos os dados disponíveis.
                            Log.v("HOMEinfo", "ESTÁ NA HOME");

                            BufferedReader leituraDados = new BufferedReader(new FileReader(arquivoDados));

                            arquivoDados.createNewFile();//Se e somente SE NÃO existir o arquivo especificado, iremos criá-lo para evitar erros de arquivos não encontrados.
                            Client myClient;
                            if ((aux = leituraDados.readLine()) != null) {//Se o arquivo nao estiver vazio...

                                Log.v("SERVIDOR", "DADOS SALVOS ENVIANDO");

                                while((aux2 = leituraDados.readLine()) != null){//Leia tudo que está no arquivo.
                                    aux += "\n" + aux2;
                                    aux2 = null;
                                }aux +=  "\n";

                                aux += "\n" + "\n\nTempo atual: " + System.currentTimeMillis() + "\n" + info.getInfo() + "\n\n" + locationListener.getMyLocation() + "\n----------------\n";

                                escritor = new FileWriter(arquivoDados, false);//apaga o buffer de dados e o fecha.
                                escritor.write("");
                                escritor.close();

                                myClient = new Client(ip, porta, aux);//Envie para o servidor os dados que estavam salvos.
                                myClient.execute();//A quebra de linha após o aux é para alinhar os dados do arquivo com os do servidor da forma que são recebidos.

                            }else{//Se o arquivo estiver vazio...

                                Log.v("SERVIDOR", "DADOS ENVIANDO");

                                aux = "\n" + "\n\nTempo atual: " + System.currentTimeMillis() + "\n" + info.getInfo() + "\n\n" + locationListener.getMyLocation() + "\n----------------\n";//calendario.get(Calendar.HOUR_OF_DAY) + ":" + calendario.get(Calendar.MINUTE) + ":" + calendario.get(Calendar.SECOND) + "," + calendario.get(Calendar.MILLISECOND)
                                myClient = new Client(ip, porta, aux);//Envia somente os dados atuais.
                                myClient.execute();

                            }

                            intente = new Intent("com.example.patrick.ALERTA_HOME");
                            intente.putExtra("HOME", true);
                            intente.putExtra("DADOS", aux);
                            sendBroadcast(intente);


                        }else{//Se nao houver condições de enviar ao servidor, guarde os dados num arquivo.

                            Log.v("HOMEinfo", "NÃO ESTÁ NA HOME");

                            aux = "\n\nTempo atual: " + System.currentTimeMillis() + "\n" + info.getInfo() + "\n\n" + locationListener.getMyLocation() + "\n----------------\n";

                            escritor = new FileWriter(arquivoDados, true);
                            escritor.write(aux);
                            escritor.close();

                            intente = new Intent("com.example.patrick.ALERTA_HOME");
                            intente.putExtra("HOME", false);
                            intente.putExtra("DADOS", aux);
                            sendBroadcast(intente);

                        }

                } catch (IOException e) {
                    e.printStackTrace();
                }

                if(++contador<40) {

                    handler.postDelayed(this, 1000);//O serviço se repete múltiplas vezes seguidas para garantir que estamos recebendo uma leitura correta dos sensores.

                } else if(++contadorDeLongoPrazo<2){//Após sucessivas repetições, aguardamos um longo período de tempo para realizar uma nova amostragem.

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

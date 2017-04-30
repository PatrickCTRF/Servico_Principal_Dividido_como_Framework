package com.example.patrick.servico_principal;

import android.app.AlarmManager;
import android.app.PendingIntent;
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

import static android.widget.Toast.LENGTH_LONG;
import static java.lang.Double.parseDouble;

/**
 * Created by patrick on 24/03/17.
 */

public class ServicoColetaDados extends Service {

    final Handler handler = new Handler();
    final AquisicaoSensores info = new AquisicaoSensores(this);
    double home_latitude = 0, home_longitude = 0;
    String ip = "10.10.25.184", aux = null, aux2 = null, modo_Desempenho;//ip antigo = "192.168.0.105"
    int porta = 6789;
    boolean registrouAlertas = false;
    Conectividade conexao = new Conectividade(this);
    Intent intente = null;
    FileWriter escritor;
    BufferedReader checkpoint_Tensao;
    AlarmManager alarm;
    PendingIntent pendingIntent;
    Client myClient;


    File arquivoDados = new File(Environment.getExternalStorageDirectory().toString() + "/" + "_InformacoesDaVidaDoUsuario.txt");

    File arquivoHome = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Latitude_Longitude_Home.txt");

    File arquivoEixoX = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Eixo_X_Tempo.txt");
    File arquivoEixoY = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Eixo_Y_Bateria.txt");
    File arquivoTensaoInicial = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Tensao_ao_desconectar_carregador.txt");
    File arquivoTempoInicial = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Momento_ao_desconectar_carregador.txt");

    File arquivoModo = new File(Environment.getExternalStorageDirectory().toString() + "/" + "Modo_Atual.txt");

    //Obtém sua localização atual
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
        conexao.isConnectedWifi();
        locationListener.getMyLocation();

        alarm = (AlarmManager) getBaseContext().getSystemService(Context.ALARM_SERVICE);
        pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.example.patrick.START_SERVICOCOLETA_DADOS"),  PendingIntent.FLAG_UPDATE_CURRENT);

        try {

            BufferedReader bufferLeitura = new BufferedReader(new FileReader(arquivoModo));

            modo_Desempenho = bufferLeitura.readLine();
            bufferLeitura.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        runnableCode = new Runnable() {

            private int contador = 0;

            @Override
            public void run() {

                Log.v("SERVICO", "O ServicoColetaDados foi chamado. Contador: " + contador + "  Contador De Longo Prazo: ");

                //locationListener.getMyLocation();//Solicita as atualizações de local

                if(!registrouAlertas){
                    Log.v("ALERTA DE PROXIMIDADE", "TENTANDO CHAMAR ALERTAS...");

                    try {
                        BufferedReader bufferLeitura = new BufferedReader(new FileReader(arquivoHome));

                        home_latitude = parseDouble(bufferLeitura.readLine());
                        home_longitude = parseDouble(bufferLeitura.readLine());
                        bufferLeitura.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    locationListener.registraAlertaDeProximidade(home_latitude, home_longitude, (float) 22);//Registramos e solicitamos o alerta de proximidade.
                    registrouAlertas = true;
                }

                if(++contador<40 && (locationListener.getIncerteza()>22 || info.getStatusString() == null)) {
                    Log.v("Serviço Coleta", "Não há precisão");
                    handler.postDelayed(this, 1000);//O serviço se repete múltiplas vezes seguidas para garantir que estamos recebendo uma leitura correta dos sensores.

                } else if(contador < 40){
                    Log.v("Serviço Coleta", "Há precisão. Contador: " + contador);

                    try {

                        if (locationListener.isInHome && conexao.isConnectedWifi()) {//Considera-se que na home comm conexão wifi há condições de enviar ao servidor.
                            //Se houver condição de enviar os dados ao servidor, envie todos os dados disponíveis.
                            Log.v("HOMEinfo", "ESTÁ NA HOME");

                            BufferedReader leituraDados = new BufferedReader(new FileReader(arquivoDados));

                            arquivoDados.createNewFile();//Se e somente SE NÃO existir o arquivo especificado, iremos criá-lo para evitar erros de arquivos não encontrados.

                            if ((aux = leituraDados.readLine()) != null) {//Se o arquivo nao estiver vazio...

                                Log.v("SERVIDOR", "DADOS SALVOS ENVIANDO");

                                while ((aux2 = leituraDados.readLine()) != null) {//Leia tudo que está no arquivo.
                                    aux += "\n" + aux2;
                                    aux2 = null;
                                }
                                aux += "\n";

                                aux += "\n" + "\n\nTempo atual: " + System.currentTimeMillis() + "\n" + info.getInfo() + "\n\n" + locationListener.getMyLocation() + "\n----------------\n";

                                escritor = new FileWriter(arquivoDados, false);//apaga o buffer de dados e o fecha.
                                escritor.write("");
                                escritor.close();

                                myClient = new Client(ip, porta, aux);//Envie para o servidor os dados que estavam salvos.
                                myClient.execute();//A quebra de linha após o aux é para alinhar os dados do arquivo com os do servidor da forma que são recebidos.

                            } else {//Se o arquivo estiver vazio...

                                Log.v("SERVIDOR", "DADOS ENVIANDO");

                                aux = "\n" + "\n\nTempo atual: " + System.currentTimeMillis() + "\n" + info.getInfo() + "\n\n" + locationListener.getMyLocation() + "\n----------------\n";//calendario.get(Calendar.HOUR_OF_DAY) + ":" + calendario.get(Calendar.MINUTE) + ":" + calendario.get(Calendar.SECOND) + "," + calendario.get(Calendar.MILLISECOND)
                                myClient = new Client(ip, porta, aux);//Envia somente os dados atuais.
                                myClient.execute();

                            }

                            intente = new Intent("com.example.patrick.ALERTA_HOME");
                            intente.putExtra("HOME", true);
                            intente.putExtra("DADOS", aux);
                            sendBroadcast(intente);


                        } else {//Se nao houver condições de enviar ao servidor, guarde os dados num arquivo.

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


//=========================DADOS PARA A REGRESSÃO LINEAR========================================================================================================================================================"



                        Log.v("MMQ", "Verificando se está descarregando.");
                        if(info.getStatusString().equals("Discharging")) {//Só coleta pontos para a regressão de consumo se a bateria estiver sendo usada como alimentação.
                            Log.v("MMQ", "Escrevendo mais um ponto de amostragem nos arquivos vetores.");

                            arquivoTensaoInicial.createNewFile();
                            checkpoint_Tensao = new BufferedReader(new FileReader(arquivoTensaoInicial));
                            arquivoEixoY.createNewFile();//Garantindo que o arquivo existe.
                            escritor = new FileWriter(arquivoEixoY, true);
                            escritor.write("" + ( 100 -Integer.parseInt(checkpoint_Tensao.readLine()) + info.getLevel()) + "\n");//Estamos normalizando o level de bateria no momento de retirada do carregador no valor máximo para facilitar a regressão.
                            escritor.close();
                            checkpoint_Tensao.close();

                            arquivoTempoInicial.createNewFile();
                            checkpoint_Tensao = new BufferedReader(new FileReader(arquivoTempoInicial));
                            arquivoEixoX.createNewFile();//Garantindo que o arquivo existe.
                            escritor = new FileWriter(arquivoEixoX, true);
                            escritor.write("" + System.currentTimeMillis() + (-Integer.parseInt(checkpoint_Tensao.readLine()) + "\n"));//Esses -10800000 são para converter o fuso horário para o horário de brasília.
                            escritor.close();
                            checkpoint_Tensao.close();

                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

//==================================FIM DA OBTENÇÃO===============================================================================================================================================
                    Log.v("ALERTA","Desligando Sensores");
                    desligaSensores();//Desliga os sensores para economizar energia até a próxima chamada do serviço.
                    contador = 0;//Reiniciamos o contador de amostragem.

                    if(modo_Desempenho.equals("economia")){alarm.set(AlarmManager.RTC, System.currentTimeMillis() + (2*3600000), pendingIntent );}//No modo de economia, baixe a cada 2 horas.
                    else{alarm.set(AlarmManager.RTC, System.currentTimeMillis() + (1800000), pendingIntent );}//No modo de desempenho, baixe a cada meia hora o arqivo.
                    handler.removeCallbacks(this);

                }else{
                    desligaSensores();
                    contador = 0;//Reiniciamos o contador de amostragem.

                    if(modo_Desempenho.equals("economia")){alarm.set(AlarmManager.RTC, System.currentTimeMillis() + (2*3600000), pendingIntent );}//No modo de economia, baixe a cada 2 horas.
                    else{alarm.set(AlarmManager.RTC, System.currentTimeMillis() + (1800000), pendingIntent );}//No modo de desempenho, baixe a cada meia hora o arqivo.
                    handler.removeCallbacks(this);
                }
            }
        };

        handler.post(runnableCode);

        return START_STICKY;
    }

    private void encaminhaDadoDepoisDeObter(){}

    private void desligaSensores(){//Este métoddo permite ao celular desligar os sensores e GPS para poupar energia.
        if(locationListener != null)locationListener.removeListener();//Deixa de requisitar atualizações ao sistema e remove este listener. Economiza energia.
        if(info != null) info.onDestroy();//Deixa de requisitar atualizações ao sistema e remove os listener. Economiza energia e evita relatório de erros.
        registrouAlertas = false;//O alerta de posição GPS agr nao estará mais registrado.
        locationListener = new Localizador(this);//Se ocorrer erro no unregisterReceiver de "removeListener", precisaremos de um novo objeto desta classe.
    }

    @Override
    public void onDestroy() {

        alarm.cancel(PendingIntent.getBroadcast(this, 0, new Intent("com.example.patrick.START_SERVICOCOLETA_DADOS"),  PendingIntent.FLAG_UPDATE_CURRENT));//Cancela quaisquer aagendamentos de chamadas para este serviço.
        if(locationListener != null)locationListener.removeListener();//Deixa de requisitar atualizações ao sistema e remove este listener. Economiza energia.
        if(info != null) info.onDestroy();//Deixa de requisitar atualizações ao sistema e remove os listener. Economiza energia e evita relatório de erros.

        Toast.makeText(this, "Service Destroyed", LENGTH_LONG).show();//Exibe na tela que o serviço foi detruído.
        handler.removeCallbacks(runnableCode);//Retira todas as chamadas agendadas deste serviço.
        super.onDestroy();

    }

}

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

/*

Para o serviço poder ser invocado e gerar arquivos a partir do boot,
ele deve ter sido chamado manualmente pelo usuário ao menos uma vez.
Nao sei ao certo se o serviço nao roda caso nao tenha sido chamado manualmente
ou se o FileWriter nao executa se nao tiver sido chamado manualmente ao menos uma vez,
mas se executarmos ele manualmente no mínimo uma vez, dá certo.

Talvez seja necessário habilitar também o envio deste ara algum Log.v para o console. Nao sei pq é necessário haver um log, mas isto ofaz funcionar no BOOT. Talvez seja uma solicitação para começar a rodar, uma conexão ou algo assim.

*/

public class MyServiceSemThread extends Service {

    final Handler handler = new Handler();
    final AquisicaoSensores info = new AquisicaoSensores(this);
    /*final EditText editTextAddress1 = MainActivity.editTextAddress;
    final EditText editTextPort1 = MainActivity.editTextPort;
    final TextView response1 = MainActivity.response;
    EditText editTextAddress = editTextAddress1;
    EditText editTextPort = editTextPort1;
    TextView response = response1;
    boolean atualiza_home = MainActivity.atualiza_home;*/
    double home_latitude = 0, home_longitude = 0;
    double margemDeErroHome = 2;
    String ip = "192.168.0.105", aux = null, aux2 = null;
    int porta = 6789;
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

                    BufferedReader bufferLeitura = new BufferedReader(new FileReader(arquivoHome));

                    home_latitude = parseDouble(bufferLeitura.readLine());
                    home_longitude = parseDouble(bufferLeitura.readLine());
                    bufferLeitura.close();

                    bufferLeitura = new BufferedReader(new FileReader(arquivoModo));

                    modo_Desempenho = bufferLeitura.readLine();
                    bufferLeitura.close();

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

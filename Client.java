package com.example.patrick.servico_principal;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Calendar;

//IP atual: 192.168.1.108;192.168.0.105

public class Client extends AsyncTask<Void, Void, Void> {

    String dstAddress;
    int dstPort;
    String response = "";
    TextView textResponse;
    String info;
    Calendar calendario = Calendar.getInstance();


    Client(String addr, int port, TextView textResponse, String info) {//addr é relativo a address = endereço web.
        dstAddress = addr;
        dstPort = port;
        this.textResponse = textResponse;
        this.info = info;
    }

    Client(String addr, int port, String info) {//addr é relativo a address = endereço web.
        dstAddress = addr;
        dstPort = port;
        this.textResponse = null;
        this.info = info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    @Override
    protected Void doInBackground(Void... arg0) {

        Socket socket = null;

        try {
            socket = new Socket(dstAddress, dstPort);

            String bytesFromServersStringRead = null;
            BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            //calendario = Calendar.getInstance();
            //socket.getOutputStream().write(("" + calendario.get(Calendar.HOUR) + ":" + calendario.get(Calendar.MINUTE) + ":" + calendario.get(Calendar.SECOND) + "," + calendario.get(Calendar.MILLISECOND) + "\n").getBytes());
            socket.getOutputStream().write((info + "\nFIM\n").getBytes());

            bytesFromServersStringRead = inputStream.readLine();
            response += bytesFromServersStringRead;

            Log.d("GOOGLE GOOGLE says:", response);

        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            response = "UnknownHostException: " + e.toString();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            File arquivoDados = new File(Environment.getExternalStorageDirectory().toString() + "/" + "_InformacoesDaVidaDoUsuario.txt");
            try {
                FileWriter escritor = new FileWriter(arquivoDados, true);//apaga o buffer de dados e o fecha.
                escritor.write(info + "\n");
                escritor.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
            response = "IOException: " + e.toString();
        }  finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        if(textResponse != null) textResponse.setText(response);
        super.onPostExecute(result);
    }

}
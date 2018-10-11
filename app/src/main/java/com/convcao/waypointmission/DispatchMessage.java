package com.convcao.waypointmission;

import android.os.AsyncTask;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

/*
public class DispatchMessage extends AsyncTask<String, Void, Void> {

    Socket s;
    DataOutputStream dos;
    PrintWriter pw;
    private static final String server_ip = "192.168.1.130";
    private static final int port = 7800;

    @Override
    protected Void doInBackground(String...voids){
        String message = voids[0];
        try {
            s = new Socket(server_ip, port);
            pw = new PrintWriter(s.getOutputStream());
            pw.write(message);
            pw.flush();
            pw.close();


        }catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
*/


public class DispatchMessage extends AsyncTask<GenericRecord, Void, Void> {

    private Socket client;
    private static final String server_ip = "192.168.1.130";
    private static final int port = 7800;
    private DatumWriter datumWriter;


    public DispatchMessage(Schema schema) {
        datumWriter = new GenericDatumWriter(schema);
    }


    @Override
    protected Void doInBackground(GenericRecord... record) {

        GenericRecord recordToSend = record[0];

        try {
            client = new Socket(server_ip, port);
            OutputStream outToServer = client.getOutputStream();
            EncoderFactory enc = new EncoderFactory();
            BinaryEncoder binaryEncoder = enc.binaryEncoder(outToServer, null);
            datumWriter.write(recordToSend, binaryEncoder);
            binaryEncoder.flush();
            outToServer.close();
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}

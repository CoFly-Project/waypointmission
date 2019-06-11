package com.convcao.waypointmission;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class DispatchMessage extends AsyncTask<GenericRecord, Void, Void> {

    private Socket client;
    private String server_ip;
    private int port;
    private DatumWriter datumWriter;
    protected static final String TAG = "DispatchMessage";
    private int connection_time_out;


    public DispatchMessage(Schema schema, String server_ip, int port, int connection_time_out) {
        this.datumWriter = new GenericDatumWriter(schema);
        this.server_ip = server_ip;
        this.port = port;
        this.connection_time_out = connection_time_out;
    }


    @Override
    protected Void doInBackground(GenericRecord... record) {

        GenericRecord recordToSend = record[0];

        try {
            client = new Socket();
            client.connect(new InetSocketAddress(server_ip, port), connection_time_out);
            //client = new Socket(server_ip, port);
            OutputStream outToServer = client.getOutputStream();
            EncoderFactory enc = new EncoderFactory();
            BinaryEncoder binaryEncoder = enc.binaryEncoder(outToServer, null);
            datumWriter.write(recordToSend, binaryEncoder);
            binaryEncoder.flush();
            outToServer.close();
            client.close();
            Log.i(TAG, record[0].getSchema().getName() + " schema sent to " + server_ip + ":" + port);
        } catch (IOException e) {
            Log.i(TAG, e.toString());
            e.printStackTrace();
        }
        return null;
    }
}

/**
 * MetadataHelper.java
 * Implements the MetadataHelper class
 * A MetadataHelper pulls Shoutcast metadata (artist, title, etc.) from a given audio stream
 * <p>
 * This file is part of
 * TRANSISTOR - Radio App for Android
 * <p>
 * Copyright (c) 2015-17 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.helpers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import org.y20k.transistor.core.Station;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/**
 * MetadataHelper class
 */
public class MetadataHelper {

    /* Define log tag */
    private static final String LOG_TAG = MetadataHelper.class.getSimpleName();


    /* Main class variables */
    private final Station mStation;
    private final Context mContext;
    private final String mStreamUri;
    private String mShoutcastProxy;
    private Socket mProxyConnection = null;
    private boolean mProxyRunning = false;
    private static Thread metaDataThread;

    /* Constructor */
    public MetadataHelper(Context context, Station station) {
        mContext = context;
        mStation = station;
        mStreamUri = station.getStreamUri().toString();
        createShoutcastProxyConnection();
    }


    /* Connect to the server, and create a listening socket on localhost,
       to stream data into the MediaPlayer, and to pull Shoutcast metadata from the stream.
       Returns localhost URL for MediaPlayer to connect to.
       Shoutcast metadata described here: http://www.smackfu.com/stuff/programming/shoutcast.html */
    private void createShoutcastProxyConnection() {
        closeShoutcastProxyConnection();
        mProxyRunning = true;
        final StringBuffer shoutcastProxyUri = new StringBuffer();

        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Socket proxy = null;
                    URLConnection connection = null;

                    try {
                        final ServerSocket proxyServer = new ServerSocket(0, 1, InetAddress.getLocalHost());
                        shoutcastProxyUri.append("http://localhost:").append(String.valueOf(proxyServer.getLocalPort())).append("/");
                        LogHelper.v(LOG_TAG, "createProxyConnection: " + shoutcastProxyUri.toString());

                        proxy = proxyServer.accept();
                        mProxyConnection = proxy;
                        proxyServer.close();

                        connection = new URL(mStreamUri).openConnection();

                        shoutcastProxyReaderLoop(proxy, connection);

                    } catch (Exception e) {
                        LogHelper.e(LOG_TAG, "Error: Unable to create proxy server. (" + e + ")");
                    }

                    mProxyRunning = false;

                    try {
                        if (connection != null) {
                            ((HttpURLConnection) connection).disconnect();
                        }
                    } catch (Exception ee) {
                        LogHelper.e(LOG_TAG, "Error: Unable to disconnect HttpURLConnection. (" + ee + ")");
                    }

                    try {
                        if (proxy != null && !proxy.isClosed()) {
                            proxy.close();
                        }
                    } catch (Exception eee) {
                        LogHelper.e(LOG_TAG, "Error: Unable to close proxy. (" + eee + ")");
                    }
                }
            }).start();

            while (shoutcastProxyUri.length() == 0) {
                try {
                    Thread.sleep(10);
                } catch (Exception e) {
                    LogHelper.e(LOG_TAG, "Error: Unable to Thread.sleep. (" + e + ")");
                }
            }
            mShoutcastProxy = shoutcastProxyUri.toString();

        } catch (Exception e) {
            LogHelper.e(LOG_TAG, "createProxyConnection: Cannot create new listening socket on localhost: " + e.toString());
            mProxyRunning = false;
            mShoutcastProxy = "";
        }
    }


    /* Closes proxy connection asynchronously */
    private void closeShoutcastProxyConnectionAsync() {
        try {
            if (mProxyConnection != null && !mProxyConnection.isClosed()) {
                mProxyConnection.close(); // terminate proxy thread loop
            }
        } catch (Exception e) {
            LogHelper.e(LOG_TAG, "closeShoutcastProxyConnectionAsync: Unable to close proxy connection: " + e.toString());
        }
    }


    /* Extract station metadata from URL connection */
    private void shoutcastProxyReaderLoop(Socket proxy, URLConnection connection) throws IOException {

        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Icy-MetaData", "1");
        connection.connect();

        InputStream in = connection.getInputStream();

        OutputStream out = proxy.getOutputStream();
        out.write(("HTTP/1.0 200 OK\r\n" +
                "Pragma: no-cache\r\n" +
                "Content-Type: " + connection.getContentType() +
                "\r\n\r\n").getBytes(StandardCharsets.UTF_8));

        byte buf[] = new byte[16384]; // one second of 128kbit stream
        int count = 0;
        int total = 0;
        int metadataSize = 0;
        final int metadataOffset = connection.getHeaderFieldInt("icy-metaint", 0);
        int bitRate = Math.max(connection.getHeaderFieldInt("icy-br", 128), 32);
        LogHelper.v(LOG_TAG, "createProxyConnection: connected, icy-metaint " + metadataOffset + " icy-br " + bitRate);
        while (true) {
            count = Math.min(in.available(), buf.length);
            if (count <= 0) {
                count = Math.min(bitRate * 64, buf.length); // buffer half-second of stream data
            }
            if (metadataOffset > 0) {
                count = Math.min(count, metadataOffset - total);
            }

            count = in.read(buf, 0, count);
            if (count == 0) {
                continue;
            }
            if (count < 0) {
                break;
            }

            out.write(buf, 0, count);

            total += count;
            if (metadataOffset > 0 && total >= metadataOffset) {
                // read metadata
                total = 0;
                count = in.read();
                if (count < 0) {
                    break;
                }
                count *= 16;
                metadataSize = count;
                if (metadataSize == 0) {
                    continue;
                }
                // maximum metadata length is 4080 bytes
                total = 0;
                while (total < metadataSize) {
                    count = in.read(buf, total, count);
                    if (count < 0) {
                        break;
                    }
                    if (count == 0) {
                        continue;
                    }
                    total += count;
                    count = metadataSize - total;
                }
                total = 0;
                String[] metadata = new String(buf, 0, metadataSize, StandardCharsets.UTF_8).split(";");
                for (String s : metadata) {
                    if (s.indexOf(TransistorKeys.SHOUTCAST_STREAM_TITLE_HEADER) == 0 &&
                            s.length() >= TransistorKeys.SHOUTCAST_STREAM_TITLE_HEADER.length() + 1) {
                        handleMetadataString(s.substring(TransistorKeys.SHOUTCAST_STREAM_TITLE_HEADER.length(), s.length() - 1));
                        // break;
                    }
                }
            }
        }
    }


    /* Extract station metadata from URL connection */
    public static void prepareMetadata(final String mStreamUri, final Station mStation, final Context mContext) throws IOException {
        metaDataThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    InputStream in;
                    int metadataOffsetInit = 0;
                    int icyInit = 0;
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        OkHttpClient client = new OkHttpClient();
                        OkHttpClient eagerClient = client.newBuilder()
                                .readTimeout(5000, TimeUnit.MILLISECONDS)
                                .connectTimeout(5000, TimeUnit.MILLISECONDS)
                                .build();

                        Request request = new Request.Builder()
                                .url(mStreamUri)
                                .build();
                        Response response = client.newCall(request).execute();
                        in = response.body().byteStream();
                        metadataOffsetInit =  Integer.parseInt(response.header("icy-metaint","0"));
                        icyInit =  Integer.parseInt(response.header("icy-br","128"));
                    }else{
                        URLConnection connection = new URL(mStreamUri).openConnection();
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(5000);
                        connection.setRequestProperty("Icy-MetaData", "1");
                        connection.connect();

                        in = connection.getInputStream();
                        metadataOffsetInit =  connection.getHeaderFieldInt("icy-metaint", 0);
                        icyInit =  connection.getHeaderFieldInt("icy-br", 128);
                    }




                    byte buf[] = new byte[16384]; // one second of 128kbit stream
                    int count = 0;
                    int total = 0;
                    int metadataSize = 0;
                    final int metadataOffset = metadataOffsetInit;
                    int bitRate = Math.max(icyInit, 32);
                    LogHelper.v(LOG_TAG, "createProxyConnection: connected, icy-metaint " + metadataOffset + " icy-br " + bitRate);
                    Thread thisThread = Thread.currentThread();
                    int thisThreadCounter = 0;
                    while (true && metaDataThread == thisThread) {
                        if (thisThreadCounter > 20) { //only try 20 times and terminate thread to be sure getting metadata
                            LogHelper.v(LOG_TAG, "thisThreadCounter: Upper Break at thisThreadCounter=" + thisThreadCounter);
                            break;
                        }
                        thisThreadCounter++;
                        count = Math.min(in.available(), buf.length);
                        if (count <= 0) {
                            count = Math.min(bitRate * 64, buf.length); // buffer half-second of stream data
                        }
                        if (metadataOffset > 0) {
                            count = Math.min(count, metadataOffset - total);
                        }

                        count = in.read(buf, 0, count);
                        if (count == 0) {
                            continue;
                        }
                        if (count < 0) {
                            LogHelper.v(LOG_TAG, "thisThreadCounter: Break at -count < 0- thisThreadCounter=" + thisThreadCounter);
                            break;
                        }
                        total += count;
                        if (metadataOffset > 0 && total >= metadataOffset) {
                            // read metadata
                            total = 0;
                            count = in.read();
                            if (count < 0) {
                                LogHelper.v(LOG_TAG, "thisThreadCounter: Break2 at -count < 0- thisThreadCounter=" + thisThreadCounter);
                                break;
                            }
                            count *= 16;
                            metadataSize = count;
                            if (metadataSize == 0) {
                                continue;
                            }
                            // maximum metadata length is 4080 bytes
                            total = 0;
                            while (total < metadataSize) {
                                count = in.read(buf, total, count);
                                if (count < 0) {
                                    LogHelper.v(LOG_TAG, "thisThreadCounter: Break3 at -count < 0- thisThreadCounter=" + thisThreadCounter);
                                    break;
                                }
                                if (count == 0) {
                                    continue;
                                }
                                total += count;
                                count = metadataSize - total;
                            }
                            total = 0;
                            String[] metadata = new String(buf, 0, metadataSize, StandardCharsets.UTF_8).split(";");
                            for (String s : metadata) {
                                if (s.indexOf(TransistorKeys.SHOUTCAST_STREAM_TITLE_HEADER) == 0 &&
                                        s.length() >= TransistorKeys.SHOUTCAST_STREAM_TITLE_HEADER.length() + 1) {
                                    //handleMetadataString(s.substring(TransistorKeys.SHOUTCAST_STREAM_TITLE_HEADER.length(), s.length() - 1));
                                    String metadata2 = s.substring(TransistorKeys.SHOUTCAST_STREAM_TITLE_HEADER.length(), s.length() - 1);
                                    if (metadata2 != null && metadata2.length() > 0) {
                                        // send local broadcast
                                        Intent i = new Intent();
                                        i.setAction(TransistorKeys.ACTION_METADATA_CHANGED);
                                        i.putExtra(TransistorKeys.EXTRA_METADATA, metadata2);
                                        i.putExtra(TransistorKeys.EXTRA_STATION, mStation);
                                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(i);

                                        // save metadata to shared preferences
                                        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
                                        SharedPreferences.Editor editor = settings.edit();
                                        editor.putString(TransistorKeys.PREF_STATION_METADATA, metadata2);
                                        editor.apply();

                                        //done getting the metadata
                                        LogHelper.v(LOG_TAG, "thisThreadCounter: Lower Break at thisThreadCounter=" + thisThreadCounter);
                                        break;
                                    }
                                    // break;
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    LogHelper.e(LOG_TAG, e.getMessage());
                }
            }


        });
        metaDataThread.start();
    }


    /* Notifies other components and saves metadata */
    private void handleMetadataString(String metadata) {

        LogHelper.v(LOG_TAG, "Metadata: «" + metadata + "»");

        if (metadata != null && metadata.length() > 0) {
            // send local broadcast
            Intent i = new Intent();
            i.setAction(TransistorKeys.ACTION_METADATA_CHANGED);
            i.putExtra(TransistorKeys.EXTRA_METADATA, metadata);
            i.putExtra(TransistorKeys.EXTRA_STATION, mStation);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(i);

            // save metadata to shared preferences
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(TransistorKeys.PREF_STATION_METADATA, metadata);
            editor.apply();
        }
    }


    /* Closes proxy connection - wrapper for closeShoutcastProxyConnectionAsync */
    public void closeShoutcastProxyConnection() {
        try {
            while (mProxyRunning && mProxyConnection == null) {
                Thread.sleep(50); // Wait for proxyServer to initialize
            }
            closeShoutcastProxyConnectionAsync();
            mProxyConnection = null;
            while (mProxyRunning) {
                Thread.sleep(50); // Wait for thread to finish
            }
        } catch (Exception e) {
            LogHelper.e(LOG_TAG, "Unable to close proxy connection. Error: " + e);
        }

    }


    /* Getter for Shoutcast proxy */
    public String getShoutcastProxy() {
        return mShoutcastProxy;
    }

}

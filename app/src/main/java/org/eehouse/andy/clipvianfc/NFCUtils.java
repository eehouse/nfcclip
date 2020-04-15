/* -*- compile-command: "find-and-gradle.sh inDeb"; -*- */
/*
 * Copyright 2020 by Eric House (xwords@eehouse.org).  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.eehouse.andy.clipvianfc;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// Let's treat each thing to send as a huge byte array. Subclasses provide
// ways to get subarrays. In the file case, they're sections of the file. In
// the clip case they're literal subarrys, but the
// data-does-fit-one-transmission case is handled the same way as the file
// case, with restart becoming possible.

class NFCUtils {
    private static final String TAG = NFCUtils.class.getSimpleName();

    // On some devices getMaxTransceiveLength() reports a large number that
    // transceive() can't in fact deliver. We'll force a smaller number to be
    // safe. I've tried doubling this and it actually slows file transfer down!
    private static final int MY_MAX = 1024;

    private static final int HASH_LEN = 4;

    static final byte VERSION_1 = (byte)0x01;
    static private final int mFlags = NfcAdapter.FLAG_READER_NFC_A
        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
    static final byte[] HEADER = { 0x00, (byte)0xA4, 0x04, 0x00 };
    private static Sender[] sSender = {null};

    static final byte CLIP = 0x02; // whatever. Just not 0 and 1
    static final byte FILE = 0x03;

    public static enum ErrorCode {
        ERR_REMOTE_TOO_NEW,
    }

    public interface Callbacks {
        void onSendEnabled();
        void onSendComplete( boolean succeeded );
        void onProgressMade( int cur, int max );
        // void onError( ErrorCode err );
    }

    public static boolean deviceSupportsNFC(Context context)
    {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
        boolean result = null != adapter;
        return result;
    }

    public static boolean nfcEnabled( Context context )
    {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
        boolean result = null != adapter && adapter.isEnabled();
        // Log.d( TAG, "nfcEnabled() => " + result );
        return result;
    }

    // Send a file. Assumption is that it'll rarely work all in one
    // connection, so it gets batched. All state lives on the receiver. On
    // connecting, we send the receiver the name and size and checksum of the
    // file. It responds each time with the percent received and the segment
    // it wants next. We then loop sending the requested segment and
    // displaying progress based on responses received. Eventually the
    // response says the file's complete. Or that the checksum didn't match
    // and we need to start over?
    static void sendFile( Activity activity, Callbacks callbacks, File file )
    {
        FileSender reader = new FileSender( activity, callbacks, file );
        send( reader, callbacks );
    }

    static void sendClip( Activity activity, final Callbacks callbacks,
                          String mimeType, String label, ClipData.Item data )
    {
        ClipSender reader = new ClipSender( activity, callbacks, mimeType, label, data );
        send( reader, callbacks );
    }

    static void send( final Sender reader, final Callbacks callbacks )
    {
        synchronized( sSender ) {
            if ( null == sSender[0] ) {
                callbacks.onSendEnabled();

                sSender[0] = reader;
                NfcAdapter
                    .getDefaultAdapter( reader.activity() )
                    .enableReaderMode( reader.activity(), sSender[0], mFlags, null );

                // Start a thread to detect 10 seconds of failure to
                // connect. Each time we DO connect the timer's reset.

                new Thread( new Runnable() {
                        @Override
                        public void run() {
                            int counter = 0;
                            long lastConnect = 0;
                            for ( ; ; ) {
                                long curConnect = reader.getLastConnect();
                                if ( curConnect > lastConnect ) {
                                    lastConnect = curConnect;
                                    counter = 0;
                                } else {
                                    if ( ++counter == 10 ) {
                                        break; // outa here
                                    }
                                    callbacks.onProgressMade( counter, 10 );
                                }

                                try {
                                    Thread.sleep( 1 * 1000 );
                                } catch ( InterruptedException ie ) {
                                }
                                if ( reader.stopped() ) {
                                    break;
                                }
                            }
                            reader.stop();
                        }
                    }).start();
            }
        }
    }

    abstract static class Sender implements NfcAdapter.ReaderCallback {
        private Activity mActivity;
        private NfcAdapter mAdapter;
        private boolean mSendSucceeded = false;
        private Callbacks mCallbacks;
        private long mLastConnect;
        int mNextPacket = -1;
        int mMaxPacketLen;

        private Sender( Activity activity, Callbacks callbacks )
        {
            mActivity = activity;
            mCallbacks = callbacks;
        }

        Callbacks callbacks() { return mCallbacks; }
        Activity activity() { return mActivity; }

        abstract byte[] completeFirst( ByteArrayOutputStream baos );
        abstract int getTotalToSend();

        long getLastConnect() { return mLastConnect; }

        private void addBytesWithHash( ByteArrayOutputStream baos, byte[] bytes )
        {
            int hash = Arrays.hashCode( bytes );
            try {
                write( baos, hash );
                baos.write( bytes );
            } catch ( IOException ioe ) {
            }
        }

        abstract void getBytesFrom( int offset, byte[] outbuf );

        byte[] makeNext()
        {
            int offset = mMaxPacketLen * mNextPacket;
            int totalLen = getTotalToSend();
            int len = Math.min( mMaxPacketLen, totalLen - offset );
            byte[] packet = new byte[len];

            getBytesFrom( offset, packet );

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            addBytesWithHash( baos, packet );
            return baos.toByteArray();
        }

        @Override
        public void onTagDiscovered( Tag tag )
        {
            IsoDep isoDep = IsoDep.get( tag );
            try {
                isoDep.connect();
                mLastConnect = System.currentTimeMillis();
                mMaxPacketLen = isoDep.getMaxTransceiveLength();
                if ( MY_MAX < mMaxPacketLen ) {
                    mMaxPacketLen = MY_MAX;
                }
                mMaxPacketLen -= HASH_LEN;
                Log.d( TAG, "onTagDiscovered() connected; max len: %d", mMaxPacketLen );

                byte[] aidBytes = hexStr2ba( BuildConfig.NFC_AID );
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write( HEADER );
                baos.write( (byte)aidBytes.length );
                baos.write( aidBytes );
                baos.write( VERSION_1 ); // min
                baos.write( VERSION_1 ); // max

                byte[] out = completeFirst( baos );
                for ( ; ; ) {
                    byte[] response = isoDep.transceive( out );
                    if ( !processResponse( response ) ) {
                        break;
                    }
                    out = makeNext();
                    mLastConnect = System.currentTimeMillis();
                }

                isoDep.close();

                stop();
            } catch ( IOException ioe ) {
                Log.e( TAG, "got ioe: " + ioe.getMessage() );
            }
        }

        private boolean processResponse( byte[] response )
        {
            boolean shouldContinue = false;
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream( response );
                byte[] status = new byte[NFCCardService.STATUS_SUCCESS.length];
                bais.read( status );
                shouldContinue = Arrays.equals( status, NFCCardService.STATUS_SUCCESS );
                if ( shouldContinue ) {
                    int totalAvail = getTotalToSend();
                    int totalReceived = readInt( bais );
                    int nextPacket = readInt( bais );
                    mNextPacket = nextPacket;
                    shouldContinue = totalReceived < totalAvail;
                    if ( shouldContinue ) {
                        callbacks().onProgressMade( totalReceived, totalAvail );
                    }
                }
            } catch ( IOException ioe ) {
                Log.e( TAG, "processResponse: ioe: %s", ioe );
                Assert.fail();
            }
            return shouldContinue;
        }

        void stop()
        {
            synchronized( sSender ) {
                if ( this == sSender[0] ) {
                    NfcAdapter
                        .getDefaultAdapter( mActivity )
                        .disableReaderMode( mActivity );
                    mCallbacks.onSendComplete( mSendSucceeded );
                    sSender[0] = null;
                }
            }
        }

        boolean stopped()
        {
            synchronized ( sSender ) {
                return sSender[0] != this;
            }
        }
    }

    private static class ClipSender extends Sender {
        private String mType;
        private String mLabel;
        private ClipData.Item mData;
        private byte[] mDataBuf;   // the whole thing we want to send

        private ClipSender( Activity activity, Callbacks callbacks, String mimeType, String label,
                            ClipData.Item data )
        {
            super( activity, callbacks );
            mType = mimeType;
            mLabel = label;
            mData = data;
            mNextPacket = -1;
        }

        @Override
        byte[] completeFirst( ByteArrayOutputStream baos )
        {
            byte[] result = null;
            try {
                ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
                write( dataStream, null == mType ? "" : mType );
                write( dataStream, null == mLabel ? "" : mLabel );
                write( dataStream, mData.coerceToText(activity()).toString() );
                mDataBuf = dataStream.toByteArray();

                String sum = getMd5Sum( mDataBuf );
                MultiPartReceiver.writeHeader( baos, CLIP, mDataBuf.length, mMaxPacketLen, sum );

                result = baos.toByteArray();
            } catch ( IOException ioe ) {
                Assert.fail();
            }
            return result;
        }

        @Override
        int getTotalToSend() { return mDataBuf.length; }

        @Override
        void getBytesFrom( int offset, byte[] outbuf )
        {
            System.arraycopy( mDataBuf, offset, outbuf, 0, outbuf.length );
        }
    } // class ClipSender

    private static class FileSender extends Sender {
        private File mFile;
        private String mSum;
        private FileSender( Activity activity, Callbacks callbacks, File file )
        {
            super( activity, callbacks );
            mFile = file;
            mSum = getMd5Sum( mFile );
        }

        @Override
        int getTotalToSend() { return (int)mFile.length(); }

        @Override
        void getBytesFrom( int offset, byte[] outbuf )
        {
            try {
                RandomAccessFile raf = new RandomAccessFile( mFile, "r" );
                raf.seek( offset );
                raf.readFully( outbuf );
            } catch ( IOException fnf ) {
                Assert.fail();
            }
        }

        @Override
        byte[] completeFirst( ByteArrayOutputStream baos )
        {
            byte[] result = null;
            try {
                MultiPartReceiver.writeHeader( baos, FILE, (int)mFile.length(),
                                               mMaxPacketLen, mSum );
                
                write( baos, mFile.getName() );

                result = baos.toByteArray();
            } catch ( IOException ioe ) {
                Assert.fail();
            }
            // Log.d( TAG, "completeFirst() => %s", hexDump( result ) );
            return result;
        }
    } // class FileSender

    private static final String HEX_CHARS = "0123456789ABCDEF";
    static byte[] hexStr2ba( String data )
    {
        data = data.toUpperCase();
        assert( 0 == data.length() % 2 );
        byte[] result = new byte[data.length() / 2];

        for ( int ii = 0; ii < data.length(); ii += 2 ) {
            int one = HEX_CHARS.indexOf(data.charAt(ii));
            assert one >= 0;
            int two = HEX_CHARS.indexOf(data.charAt(ii + 1));
            assert two >= 0;
            result[ii/2] = (byte)((one << 4) | two);
        }

        return result;
    }

    static String hexDump( byte[] bytes )
    {
        String result = "<null>";
        if ( null != bytes ) {
            StringBuilder dump = new StringBuilder();
            for ( byte byt : bytes ) {
                dump.append( String.format( "%02x ", byt ) );
            }
            result = dump.toString();
        }
        return result;
    }

    static void write( ByteArrayOutputStream stream, String str ) throws IOException
    {
        DataOutputStream dos = new DataOutputStream(stream);
        dos.writeUTF( str );
        dos.flush();
    }

    static void write( ByteArrayOutputStream stream, int val ) throws IOException
    {
        // Log.d( TAG, "write(int=%d)", val );
        DataOutputStream dos = new DataOutputStream(stream);
        dos.writeInt( val );
        dos.flush();
    }

    static void write( ByteArrayOutputStream stream, long val ) throws IOException
    {
        DataOutputStream dos = new DataOutputStream(stream);
        dos.writeLong( val );
        dos.flush();
    }

    static String readString( ByteArrayInputStream stream ) throws IOException
    {
        DataInputStream dis = new DataInputStream(stream);
        return dis.readUTF();
    }

    static int readInt( ByteArrayInputStream stream ) throws IOException
    {
        DataInputStream dis = new DataInputStream(stream);
        int result = dis.readInt();
        // Log.d( TAG, "readInt() => %d", result );
        return result;
    }

    static long readLong( ByteArrayInputStream stream ) throws IOException
    {
        DataInputStream dis = new DataInputStream(stream);
        return dis.readLong();
    }

    abstract static class Receiver {
        abstract byte[] receiveFirst();
        abstract byte[] receive( byte[] apdu );
    }

    private static abstract class MultiPartReceiver extends Receiver {
        Context mContext;
        int mEventualSize;
        int mMaxPacketLen;
        int mPacketCount;       // how many packets will it take?
        String mSum;
        int mNextPacket;

        MultiPartReceiver( Context context, ByteArrayInputStream bais )
        {
            mContext = context;
            try {
                mEventualSize = readInt( bais );
                mMaxPacketLen = readInt( bais );
                mPacketCount = (mEventualSize + mMaxPacketLen - 1) / mMaxPacketLen;
                mSum = readString( bais );
                Log.d( TAG, "MultiPartReceiver(): read len %d, maxLen %d, sum: %s",
                       mEventualSize, mMaxPacketLen, mSum );
            } catch ( IOException ioe ) {
                Assert.fail();
            }
        }

        abstract void writeRequest( ByteArrayOutputStream baos ) throws IOException;
        Context context() { return mContext; }

        @Override
        byte[] receiveFirst()
        {
            Log.d( TAG, "receiveFirst()" );
            byte[] result = null;
            try {
                checkFinished();

                // Called after the initial packet checks out. We need to write
                // back what we want/need
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write( NFCCardService.STATUS_SUCCESS );
                writeRequest( baos );
                result = baos.toByteArray();
            } catch ( IOException ioe ) {
                Assert.fail();
            }
            return result;
        }

        abstract void store( int packetNo, byte[] packet ) throws IOException;
        abstract void checkFinished();

        @Override
        byte[] receive( byte[] apdu )
        {
            byte[] result = null;
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream( apdu );
                int hash = readInt( bais );
                byte[] rest = new byte[bais.available()];
                bais.read( rest );
                int restHash = Arrays.hashCode( rest );

                ByteArrayOutputStream response = new ByteArrayOutputStream();
            
                if ( restHash == hash ) {
                    store( mNextPacket, rest );
                    // mBuffer.write( rest );
                    checkFinished();

                    response.write( NFCCardService.STATUS_SUCCESS );
                    writeRequest( response );
                } else {
                    Log.e( TAG, "checksums don't match; bailing" );
                    response.write( NFCCardService.STATUS_FAILED );
                }

                result = response.toByteArray();
            } catch ( IOException ioe ) {
                Assert.fail();
            }
            return result;
        }

        private static void writeHeader( ByteArrayOutputStream baos, byte cmd,
                                         int fullLen, int maxPacketLen, String sum )
            throws IOException
        {
            baos.write( cmd );
            write( baos, fullLen );
            write( baos, maxPacketLen );
            write( baos, sum );
        }
    } // pclass MultiPartReceiver

    // We'll keep clip data in memory. We still recover from a partial
    // transmission as long as the app isn't restarted
    static Map<String, ByteArrayOutputStream> sPendingClips = new HashMap<>();

    private static ByteArrayOutputStream getBuffer( String sum )
    {
        synchronized ( sPendingClips ) {
            ByteArrayOutputStream baos;
            if ( !sPendingClips.containsKey( sum ) ) {
                sPendingClips.put( sum, new ByteArrayOutputStream() );
            }
            return sPendingClips.get( sum );
        }
    }

    private static void clearBuffer( String sum )
    {
        synchronized ( sPendingClips ) {
            sPendingClips.remove( sum );
        }
    }

    static class ClipReceiver extends MultiPartReceiver {
        private ByteArrayOutputStream mBuffer;

        private ClipReceiver( Context context, ByteArrayInputStream bais )
        {
            super( context, bais );
            mBuffer = getBuffer( mSum );
        }

        @Override
        void writeRequest( ByteArrayOutputStream baos ) throws IOException
        {
            int curPacketCount = mBuffer.size() / mMaxPacketLen;
            write( baos, mBuffer.size() );
            mNextPacket = curPacketCount;
            write( baos, curPacketCount );
            Log.d( TAG, "writeRequest(): asking for packet %d (of %d)", curPacketCount, mPacketCount );
        }

        @Override
        void store( int packetNo, byte[] packet ) throws IOException
        {
            Assert.assertTrue( packetNo == mNextPacket );
            mBuffer.write( packet );
        }

        @Override
        void checkFinished()
        {
            Assert.assertTrue( mBuffer.size() <= mEventualSize );
            if ( mBuffer.size() == mEventualSize ) {
                byte[] buffer = mBuffer.toByteArray();
                String sum = getMd5Sum( buffer );
                if ( ! sum.equals( mSum ) ) {
                    Log.e( TAG, "checksum mismatch!!!!" );
                } else {
                    Log.d( TAG, "checksums match! We got it!!!" );
                    clearBuffer( mSum );

                    Log.d( TAG, "processing %d bytes", buffer.length );
                    try {
                        ByteArrayInputStream bais = new ByteArrayInputStream( buffer );

                        String mimeType = readString( bais );
                        String label = readString( bais );
                        String data = readString( bais );
                        Clip.setData( context(), mimeType, label, data );
                        Notify.post( context(), data );
                    } catch ( IOException ioe ) {
                        Log.e( TAG, "processBuffer(): exception: " + ioe );
                    }
                }
            }
        }
        
        private void processBuffer()
        {
            Log.d( TAG, "processBuffer()" );
            if ( null != mBuffer ) {
            } else {
                Log.e( TAG, "processBuffer(): nothing to process" );
            }
        }
    }

    static class FileReceiver extends MultiPartReceiver {
        private FileStore mFileStore;
        private String mFileName;

        private FileReceiver( Context context, ByteArrayInputStream bais )
        {
            super( context, bais );
            try {
                mFileName = readString( bais );
            } catch ( IOException ioe ) {
                Assert.fail();
            }
            Log.d( TAG, "read file name: %s", mFileName );
        }

        @Override
        byte[] receiveFirst()
        {
            mFileStore = FileStore.getFor( context(), mSum, mFileName,
                                           mMaxPacketLen, mEventualSize );
            return super.receiveFirst();
        }

        @Override
        void writeRequest( ByteArrayOutputStream baos ) throws IOException
        {
            write( baos, mFileStore.getNBytesReceived() );
            mNextPacket = mFileStore.getNextPacketSought() ;
            // Log.d( TAG, "writeRequest(): mNextPacket now %d", mNextPacket );
            Assert.assertTrue( mNextPacket >= 0 );
            write( baos, mNextPacket );
        }

        @Override
        void store( int packetNo, byte[] packet ) throws IOException
        {
            mFileStore.store( packetNo, packet );
        }

        @Override
        void checkFinished()
        {
            Assert.assertTrue( mFileStore.getNBytesReceived() <= mEventualSize );
            if ( mFileStore.getNBytesReceived() == mEventualSize ) {
                if ( mFileStore.checkSum() ) {
                    Log.d( TAG, "file checksums match! We got it!!!" );
                    Notify.postGotFile( context(), mFileName, mSum );
                }
            }
        }
    }

    static Receiver makeReceiver( Context context, byte[] apdu ) throws Exception
    {
        // Log.d( TAG, "makeReceiver(%s)", hexDump(apdu) );
        Receiver result = null;
        ByteArrayInputStream bais = new ByteArrayInputStream( apdu );
        byte[] header = new byte[NFCUtils.HEADER.length];
        bais.read( header );
        if ( !Arrays.equals( header, NFCUtils.HEADER ) ) {
            throw new Exception("header mismatch");
        }

        byte aidLen = (byte)bais.read();
        byte[] aid = new byte[aidLen];
        bais.read( aid );
        if ( !Arrays.equals( aid, NFCUtils.hexStr2ba( BuildConfig.NFC_AID ) ) ) {
            throw new Exception("aid mismatch");
        }
        byte minVers = (byte)bais.read();
        byte maxVers = (byte)bais.read();
        if ( NFCUtils.VERSION_1 != minVers && NFCUtils.VERSION_1 != maxVers ) {
            throw new Exception("bad version codes: " + minVers + ", " + maxVers);
        }

        // Get this far? We're connected. Save the rest for later
        byte cmd = (byte)bais.read();
        switch ( cmd ) {
        case CLIP:
            result = new ClipReceiver( context, bais );
            break;
        case FILE:
            result = new FileReceiver( context, bais );
            break;
        default:
            Assert.fail();
        }
        return result;
    }

    static String getMd5Sum( File input )
    {
        String result = null;
        try {
            FileInputStream fis =  new FileInputStream( input );
            MessageDigest digest = MessageDigest.getInstance("MD5");

            byte[] buffer = new byte[1024];
            for ( ; ; ) {
                int numRead = fis.read( buffer );
                if ( numRead < 0 ) {
                    break;
                }
                digest.update( buffer, 0, numRead );
            }

            fis.close();
            result = digestToStr( digest.digest() );
        } catch ( IOException | java.security.NoSuchAlgorithmException ioe ) {
            Log.e( TAG, "processBuffer(): exception: " + ioe );
        }
        return result;
    }

    // adapted from:
    // https://stackoverflow.com/questions/4846484/md5-hashing-in-android
    static String getMd5Sum( byte[] input )
    {
        String result = null;
        try {
            MessageDigest md = MessageDigest.getInstance( "MD5" );
            byte[] messageDigest = md.digest( input );
            result = digestToStr( messageDigest );
        } catch ( java.security.NoSuchAlgorithmException e ) {
            Log.e( TAG, "MD5: %s", e.getLocalizedMessage() );
        }
        Log.d( TAG, "getMd5Sum() => %s", result );
        return result;
    }

    private static String digestToStr( byte[] digest )
    {
        BigInteger number = new BigInteger( 1, digest );
        String result = number.toString(16);

        while ( result.length() < 32 ) {
            result = "0" + result;
        }
        return result;
    }
}

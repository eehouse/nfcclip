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
import java.io.IOException;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

// Let's treat each thing to send as a huge byte array. Subclasses provide
// ways to get subarrays. In the file case, they're sections of the file. In
// the clip case they're literal subarrys, but the
// data-does-fit-one-transmission case is handled the same way as the file
// case, with restart becoming possible.

class NFCUtils {
    private static final String TAG = NFCUtils.class.getSimpleName();

    // On some devices getMaxTransceiveLength() reports a large number that
    // transceive() can't in fact deliver. We'll force a smaller number to be
    // safe.
    private static final int MY_MAX = 1024;
    private static final int HASH_LEN = 4;

    static final byte VERSION_1 = (byte)0x01;
    static private final int mFlags = NfcAdapter.FLAG_READER_NFC_A
        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
    static final byte[] HEADER = { 0x00, (byte)0xA4, 0x04, 0x00 };
    private static Reader[] sReader = {null};

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
        FileReader reader = new FileReader( activity, callbacks, file );
        send( reader );
    }

    static void sendClip( Activity activity, final Callbacks callbacks,
                          String mimeType, String label, ClipData.Item data )
    {
        ClipReader reader = new ClipReader( activity, callbacks, mimeType, label, data );
        send( reader );
    }

    static void send( final Reader reader )
    {
        synchronized( sReader ) {
            if ( null == sReader[0] ) {
                reader.callbacks().onSendEnabled();

                sReader[0] = reader;
                NfcAdapter
                    .getDefaultAdapter( reader.activity() )
                    .enableReaderMode( reader.activity(), sReader[0], mFlags, null );
                new Thread( new Runnable() {
                        @Override
                        public void run() {
                            for ( int ii = 0; ii < 10; ++ii ) {
                                reader.callbacks().onProgressMade( ii, 10 );
                                try {
                                    Thread.sleep(1 * 1000);
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

    abstract static class Reader implements NfcAdapter.ReaderCallback {
        private Activity mActivity;
        private NfcAdapter mAdapter;
        private boolean mSendSucceeded = false;
        private Callbacks mCallbacks;
        private boolean mConnected;
        int mMaxPacketLen;

        private Reader( Activity activity, Callbacks callbacks )
        {
            mActivity = activity;
            mCallbacks = callbacks;
        }

        Callbacks callbacks() { return mCallbacks; }
        Activity activity() { return mActivity; }

        abstract byte[] completeFirst( ByteArrayOutputStream baos );
        abstract boolean processResponse( byte[] response );
        abstract byte[] makeNext();

        boolean isSending() { return mConnected; }

        @Override
        public void onTagDiscovered( Tag tag )
        {
            IsoDep isoDep = IsoDep.get( tag );
            try {
                isoDep.connect();
                mConnected = true;
                mMaxPacketLen = isoDep.getMaxTransceiveLength();
                if ( MY_MAX < mMaxPacketLen ) {
                    mMaxPacketLen = MY_MAX;
                }
                mMaxPacketLen -= HASH_LEN;
                Log.d( TAG, "onTagDiscovered() connected; max len: " + mMaxPacketLen );

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
                }

                isoDep.close();

                stop();
            } catch ( IOException ioe ) {
                Log.e( TAG, "got ioe: " + ioe.getMessage() );
            }
            mConnected = false;
        }

        void stop()
        {
            synchronized( sReader ) {
                if ( this == sReader[0] ) {
                    NfcAdapter
                        .getDefaultAdapter( mActivity )
                        .disableReaderMode( mActivity );
                    mCallbacks.onSendComplete( mSendSucceeded );
                    sReader[0] = null;
                }
            }
        }

        boolean stopped()
        {
            synchronized ( sReader ) {
                return sReader[0] != this;
            }
        }
    }

    private static class ClipReader extends Reader {
        private String mType;
        private String mLabel;
        private ClipData.Item mData;
        private byte[] mDataBuf;   // the whole thing we want to send
        private int mNextPacket = -1;

        private ClipReader( Activity activity, Callbacks callbacks, String mimeType, String label,
                            ClipData.Item data )
        {
            super( activity, callbacks );
            mType = mimeType;
            mLabel = label;
            mData = data;
        }

        @Override
        byte[] completeFirst( ByteArrayOutputStream baos )
        {
            byte[] result = null;
            try {
                baos.write( CLIP );

                ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
                write( dataStream, null == mType ? "" : mType );
                write( dataStream, null == mLabel ? "" : mLabel );
                write( dataStream, mData.coerceToText(activity()).toString() );
                mDataBuf = dataStream.toByteArray();

                write( baos, mDataBuf.length );
                write( baos, mMaxPacketLen );
                String sum = getMd5Sum( mDataBuf );
                write( baos, sum );
                Log.d( TAG, "completeFirst() wrote sum %s", sum );

                result = baos.toByteArray();
            } catch ( IOException ioe ) {
                Assert.fail();
            }
            return result;
        }

        @Override
        boolean processResponse( byte[] response )
        {
            boolean shouldContinue = false;
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream( response );
                byte[] status = new byte[NFCCardService.STATUS_SUCCESS.length];
                bais.read( status );
                shouldContinue = Arrays.equals( status, NFCCardService.STATUS_SUCCESS );
                if ( shouldContinue ) {
                    int totalRead = readInt( bais );
                    int nextPacket = readInt( bais );
                    Log.d( TAG, "processResponse(): totalRead: %d; mNextPacket: %d; nextPacket: %d",
                           totalRead, mNextPacket, nextPacket );
                    Assert.assertTrue( nextPacket == (mNextPacket + 1) );
                    mNextPacket = nextPacket;
                    shouldContinue = totalRead < mDataBuf.length;
                    if ( shouldContinue ) {
                        callbacks().onProgressMade( totalRead, mDataBuf.length );
                    }
                }
            } catch ( IOException ioe ) {
                Assert.fail();
            }
            return shouldContinue;
        }

        @Override
        byte[] makeNext()
        {
            int offset = mMaxPacketLen * mNextPacket;
            int len = Math.min( mMaxPacketLen, mDataBuf.length - offset );
            byte[] data = new byte[len];
            System.arraycopy( mDataBuf, offset, data, 0, len );
            int hash = Arrays.hashCode( data );

            byte[] result = null;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int posBefore = baos.size();
                write( baos, hash );
                int posAfter = baos.size();
                Assert.assertTrue( HASH_LEN == posAfter - posBefore );
                baos.write( data );
                result = baos.toByteArray();
            } catch ( IOException ioe ) {
                Assert.fail();
            }
            return result;
        }
    }

    private static class FileReader extends Reader {
        private File mFile;
        private FileReader( Activity activity, Callbacks callbacks, File file )
        {
            super( activity, callbacks );
            mFile = file;
        }

        @Override
        byte[] makeNext() { return null; }
        @Override
        byte[] completeFirst( ByteArrayOutputStream baos ) { return null; }
        @Override
        boolean processResponse( byte[] response ) { return false; }

    }

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
        DataOutputStream dos = new DataOutputStream(stream);
        dos.writeInt( val );
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
        return dis.readInt();
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

        Context context() { return mContext; }
    }

    static class ClipReceiver extends MultiPartReceiver {
        private ByteArrayOutputStream mBuffer;

        private ClipReceiver( Context context, ByteArrayInputStream bais )
        {
            super( context, bais );
        }

        @Override
        byte[] receiveFirst()
        {
            byte[] result = null;
            try {
                mBuffer = new ByteArrayOutputStream();
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

        private void writeRequest( ByteArrayOutputStream baos ) throws IOException
        {
            int curPacketCount = mBuffer.size() / mMaxPacketLen;
            write( baos, mBuffer.size() );
            write( baos, curPacketCount );
            Log.d( TAG, "writeRequest(): asking for packet %d (of %d)", curPacketCount, mPacketCount );
        }

        @Override
        byte[] receive( byte[] apdu )
        {
            byte[] result = null;
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream( apdu );
                int sum = readInt( bais );
                byte[] rest = new byte[bais.available()];
                bais.read( rest );
                int restSum = Arrays.hashCode( rest );

                ByteArrayOutputStream response = new ByteArrayOutputStream();
            
                if ( restSum == sum ) {
                    mBuffer.write( rest );
                    Log.d( TAG, "receive(); now have %d of %d bytes", mBuffer.size(), mEventualSize );
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

        private void checkFinished()
        {
            Assert.assertTrue( mBuffer.size() <= mEventualSize );
            if ( mBuffer.size() == mEventualSize ) {
                byte[] buffer = mBuffer.toByteArray();
                String sum = getMd5Sum( buffer );
                if ( ! sum.equals( mSum ) ) {
                    Log.e( TAG, "checksum mismatch!!!!" );
                } else {
                    Log.d( TAG, "checksums match! We got it!!!" );
                    mBuffer = null;

                    Log.d( TAG, "processing " + buffer.length + " bytes" );
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
        private FileReceiver( Context context, ByteArrayInputStream bais )
        {
            super( context, bais );
        }

        @Override
        byte[] receiveFirst()
        {
            return null;
        }

        @Override
        byte[] receive( byte[] apdu )
        {
            return null;
        }
    }

    static Receiver makeReceiver( Context context, byte[] apdu ) throws Exception
    {
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

    // adapter from:
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

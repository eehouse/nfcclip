/* -*- compile-command: "find-and-gradle.sh inXw4dDeb"; -*- */
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
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

class NFCUtils {
    private static final String TAG = NFCUtils.class.getSimpleName();
    static final byte VERSION_1 = (byte)0x01;
    static private final int mFlags = NfcAdapter.FLAG_READER_NFC_A
        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
    static final byte[] HEADER = { 0x00, (byte)0xA4, 0x04, 0x00 };

    static void sendClip(Activity activity, String data )
    {
        Reader reader = new Reader( activity, data );
        NfcAdapter
            .getDefaultAdapter( activity )
            .enableReaderMode( activity, reader, mFlags, null );
    }

    private static class Reader implements NfcAdapter.ReaderCallback {
        private Activity mActivity;
        private String mData;
        private NfcAdapter mAdapter;

        private Reader( Activity activity, String data )
        {
            mActivity = activity;
            mData = data;
        }

        @Override
        public void onTagDiscovered( Tag tag )
        {
            IsoDep isoDep = IsoDep.get( tag );
            try {
                isoDep.connect();
                int maxLen = isoDep.getMaxTransceiveLength();
                Log.d( TAG, "onTagDiscovered() connected; max len: " + maxLen );

                byte[] aidBytes = hexStr2ba( BuildConfig.NFC_AID );
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write( HEADER );
                baos.write( (byte)aidBytes.length );
                baos.write( aidBytes );
                baos.write( VERSION_1 ); // min
                baos.write( VERSION_1 ); // max

                // Let's try writing the data too
                write( baos, mData );

                byte[] msg = baos.toByteArray();
                assert msg.length < maxLen || !BuildConfig.DEBUG;
                Log.d( TAG, "sent " + msg.length + " bytes" );
                byte[] response = isoDep.transceive( msg );

                isoDep.close();
            } catch ( IOException ioe ) {
                Log.e( TAG, "got ioe: " + ioe.getMessage() );
            }

        NfcAdapter
            .getDefaultAdapter( mActivity )
            .disableReaderMode( mActivity );
        }
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

    static String read( ByteArrayInputStream stream ) throws IOException
    {
        DataInputStream dis = new DataInputStream(stream);
        return dis.readUTF();
    }
}

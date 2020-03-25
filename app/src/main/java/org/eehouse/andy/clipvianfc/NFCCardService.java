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

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class NFCCardService extends HostApduService {
    private static final String TAG = NFCCardService.class.getSimpleName();

    private static final byte[] STATUS_FAILED = { 0x6F, 0x00, };
    static final byte[] STATUS_SUCCESS = { (byte)0x90, 0x00, };

    private ByteArrayOutputStream mBuffer;

    @Override
    public byte[] processCommandApdu( byte[] apdu, Bundle extras )
    {
        byte[] result = STATUS_FAILED;

        if ( null != apdu ) {
            Log.d( TAG, "processCommandApdu(): received " + apdu.length + " bytes" );
            // Log.d( TAG, NFCUtils.hexDump( apdu ) );
            try {
                if ( null == mBuffer ) {
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
                    byte[] rest = new byte[bais.available()];
                    bais.read( rest );
                    mBuffer = new ByteArrayOutputStream();
                    mBuffer.write( rest );
                    Log.d( TAG, "processCommandApdu(): now have " + mBuffer.size()
                           + " bytes in buffer" );
                } else {
                    mBuffer.write( apdu );
                }

                result = STATUS_SUCCESS;
            } catch ( Exception ex ) {
                Log.e( TAG, "exception: " + ex );
            }
        } else {
            Log.e( TAG, "processCommandApdu(): apdu null!" );
        }

        return result;
    }

    @Override
    public void onDeactivated( int reason )
    {
        String str = "<other>";
        switch ( reason ) {
        case HostApduService.DEACTIVATION_LINK_LOSS:
            str = "DEACTIVATION_LINK_LOSS";
            break;
        case HostApduService.DEACTIVATION_DESELECTED:
            str = "DEACTIVATION_DESELECTED";
            break;
        }
        Log.d( TAG, "onDeactivated(reason=" + str + ")" );

        processBuffer();
    }

    private void processBuffer()
    {
        Log.d( TAG, "processBuffer()" );
        if ( null != mBuffer ) {
            byte[] buffer = mBuffer.toByteArray();
            mBuffer = null;
            Log.d( TAG, "processing " + buffer.length + " bytes" );
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream( buffer );

                int len = NFCUtils.readInt( bais );
                if ( len != bais.available() ) {
                    Log.e( TAG, "len bad: have " + bais.available() + " but expect " + len );
                } else {
                    String mimeType = NFCUtils.readString( bais );
                    String label = NFCUtils.readString( bais );
                    String data = NFCUtils.readString( bais );
                    Clip.setData( this, mimeType, label, data );
                    Notify.post( this, data );
                }
            } catch ( IOException ioe ) {
                Log.e( TAG, "processBuffer(): exception: " + ioe );
            }
        } else {
            Log.e( TAG, "processBuffer(): nothing to process" );
        }
    }
}

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

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

public class NFCCardService extends HostApduService {
    private static final String TAG = NFCCardService.class.getSimpleName();

    private static final byte[] STATUS_FAILED = { 0x6F, 0x00, };
    static final byte[] STATUS_SUCCESS = { (byte)0x90, 0x00, };

    @Override
    public byte[] processCommandApdu( byte[] apdu, Bundle extras )
    {
        byte[] result = STATUS_FAILED;
        Log.d( TAG, "processCommandApdu() called; received " + apdu.length + " bytes: " );
        Log.d( TAG, NFCUtils.hexDump( apdu ) );

        if ( null != apdu ) {
            try {
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

                String mimeType = NFCUtils.read( bais );
                String label = NFCUtils.read( bais );
                String data = NFCUtils.read( bais );
                Clip.setData( this, mimeType, label, data );

                result = STATUS_SUCCESS;
            } catch ( Exception ex ) {
                Log.e( TAG, "exception: " + ex );
            }
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
    }
}

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class NFCCardService extends HostApduService {
    private static final String TAG = NFCCardService.class.getSimpleName();

    static final byte[] STATUS_FAILED = { 0x6F, 0x00, };
    static final byte[] STATUS_SUCCESS = { (byte)0x90, 0x00, };

    private NFCUtils.Receiver mReceiver;

    @Override
    public byte[] processCommandApdu( byte[] apdu, Bundle extras )
    {
        byte[] result = STATUS_FAILED;

        if ( null != apdu ) {
            try {
                if ( null == mReceiver ) {
                    mReceiver = NFCUtils.makeReceiver( this, apdu );
                    result = mReceiver.receiveFirst();
                } else {
                    result = mReceiver.receive( apdu );
                }
            } catch ( Exception ex ) {
                Log.e( TAG, "processCommandApdu() got %s", ex );
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

        mReceiver = null;
    }
}

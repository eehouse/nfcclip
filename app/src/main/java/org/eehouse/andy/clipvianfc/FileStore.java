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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class FileStore {
    private static final String TAG = FileStore.class.getSimpleName();
    private String mSum;
    private String mName;
    private Map<Integer, byte[]> mData = new HashMap<>();
    private int mNBytesStored;
    private int mNextPacket = -1;
    private int mPacketsExpected;
    private int mMaxPacketLen;
    private int mEventualSize;

    private static Map<String, FileStore> sStoresMap = new HashMap<>();

    static FileStore getFor( String sum, String name, int maxPacketLen, int eventualSize )
    {
        FileStore result;
        synchronized ( sStoresMap ) {
            if ( ! sStoresMap.containsKey( sum ) ) {
                sStoresMap.put( sum, new FileStore( sum, name, maxPacketLen, eventualSize ) );
            }
            result = sStoresMap.get( sum );
        }
        return result;
    }

    private FileStore( String sum, String name, int maxPacketLen, int eventualSize )
    {
        mSum = sum;
        mName = name;
        mMaxPacketLen = maxPacketLen;
        mEventualSize = eventualSize;
        mPacketsExpected = (eventualSize + maxPacketLen - 1) / maxPacketLen;
    }

    int getNBytesReceived()
    {
        return mNBytesStored;
    }

    int getNextPacketSought()
    {
        int result = mNextPacket;
        for ( int ii = 0; -1 == result && ii < mPacketsExpected; ++ii ) {
            if ( ! mData.containsKey( ii ) ) {
                result = ii;
            }
        }
        Log.d( TAG, "getNextPacketSought() => %d", result );
        return result;
    }

    void store( int packetNo, byte[] packet )
    {
        Log.d( TAG, "store(packetNo=%d)", packetNo );
        if ( ! mData.containsKey( packetNo ) ) {
            mData.put( packetNo, packet );
            mNBytesStored += packet.length;

            int nextPacket = packetNo + 1;
            if ( ! mData.containsKey( nextPacket ) ) {
                mNextPacket = nextPacket;
            } else {
                mNextPacket = -1;
            }
        } else {
            Log.e( TAG, "duplicate packet %d???", packetNo );
        }
    }

    File writeFileWithSumCheck()
    {
        File result = null;
        Assert.assertTrue( mNBytesStored == mEventualSize );

        File dir = ClipFragment.getFilesDir();

        String name = mName;
        File file;
        for ( ; ; ) {
            file = new File( dir, name );
            if ( ! file.exists() ) {
                break;
            }
            name = "x" + name;
        }

        try {
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream( file );
            for ( int ii = 0; ii < mPacketsExpected; ++ii ) {
                byte[] bytes = mData.get( ii );
                fos.write( bytes );
            }

            String sum = NFCUtils.getMd5Sum( file );
            if ( sum.equals( mSum ) ) {
                Log.d( TAG, "sums check out!!!" );
                result = file;
            } else {
                Log.e( TAG, "checksum mismatch" );
            }
        } catch ( IOException ioe ) {
            ioe.printStackTrace();
        }

        return result;
    }
}

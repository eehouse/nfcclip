/* -*- compile-command: "find-and-gradle.sh inFossDeb"; -*- */
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

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

class FileStore {
    private static final String TAG = FileStore.class.getSimpleName();
    private String mSum;
    private File mTmpFile;
    private RandomAccessFile mRaf;
    private String mName;
    private int mNBytesStored;
    private int mNextPacket = -1;
    private int mPacketsExpected;
    private int mMaxPacketLen;
    private int mEventualSize;

    static FileStore getFor( Context context, String sum, String name,
                             int maxPacketLen, int eventualSize )
    {
        File dir = context.getCacheDir();
        FileStore result = new FileStore( dir, sum, name, maxPacketLen, eventualSize );
        return result;
    }

    static void writeFileTo( Context context, File dir, String name, String sum )
    {
        File destFile = new File( dir, name );
        File srcDir = context.getCacheDir();
        File srcFile = new File( srcDir, sum );

        try {
            destFile.createNewFile();
            FileInputStream fis = new FileInputStream( srcFile );
            FileOutputStream fos = new FileOutputStream( destFile );
            byte[] buffer = new byte[2048];
            for ( ; ; ) {
                int nRead = fis.read(buffer);
                if (nRead < 0) {
                    break;
                }
                fos.write( buffer);
            }
            srcFile.delete();
        } catch ( IOException ioe ) {
            ioe.printStackTrace();
            Assert.fail();
        }
    }

    private FileStore( File dir, String sum, String name, int maxPacketLen, int eventualSize )
    {
        mTmpFile = new File( dir, sum );
        try {
            if ( ! mTmpFile.exists() ) {
                mTmpFile.createNewFile();
            } else {
                Log.d( TAG, "file exists; size: %d", mTmpFile.length() );
            }
            mRaf = new RandomAccessFile( mTmpFile, "rw" );
        } catch ( IOException ioe ) {
            Assert.fail();
        }
        mNBytesStored = (int)mTmpFile.length();
        mSum = sum;
        mName = name;
        mMaxPacketLen = maxPacketLen;
        mEventualSize = eventualSize;
        mPacketsExpected = (eventualSize + maxPacketLen - 1) / maxPacketLen;
    }

    int getNBytesReceived()
    {
        Assert.assertTrue( mNBytesStored >= 0 );
        return mNBytesStored;
    }

    int getNextPacketSought()
    {
        int result = mNBytesStored / mMaxPacketLen;
        Log.d( TAG, "getNextPacketSought() => %d", result );
        Assert.assertTrue( result >= 0 );
        return result;
    }

    void store( int packetNo, byte[] packet )
    {
        Log.d( TAG, "store(packetNo=%d)", packetNo );
        try {
            mRaf.seek( packetNo * mMaxPacketLen );
            mRaf.write( packet );
            mNBytesStored += packet.length;
        } catch ( IOException ioe ) {
            Assert.fail();
        }
        Assert.assertTrue( mNBytesStored == (int)mTmpFile.length() );
    }

    boolean checkSum()
    {
        String sum = NFCUtils.getMd5Sum( mTmpFile );
        boolean result = sum.equals( mSum );
        if ( result ) {
            Log.d( TAG, "sums check out!!!" );
        } else {
            Log.e( TAG, "checksum mismatch" );
        }

        return result;
    }
}

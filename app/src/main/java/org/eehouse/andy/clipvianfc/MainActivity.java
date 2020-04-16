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

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private final static String GOT_FILE_NAME = TAG + "/name";
    private final static String GOT_FILE_SUM = TAG + "/sum";
    static final String[] PERMS_ARR = { Manifest.permission.WRITE_EXTERNAL_STORAGE };
    private static final int REQUEST_WRITE_CODE = 38279;

    private ViewPager mViewPager;

    // These need to be saved in Bundle. PENDING.
    private String mName;
    private String mSum;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if ( !NFCUtils.deviceSupportsNFC( this ) ) {
            setContentView( R.layout.activity_no_nfc );
            findViewById( R.id.uninstall )
                .setOnClickListener( new View.OnClickListener() {
                        @Override
                        public void onClick( View view ) {
                            uninstall( MainActivity.this );
                        }
                    } );
        } else {
            setContentView( R.layout.main_pager );

            mViewPager = (ViewPager)findViewById( R.id.viewpager );
            PageFragment.addAdapter( getSupportFragmentManager(), mViewPager );
            TabLayout tabLayout = (TabLayout)findViewById( R.id.sliding_tabs );
            tabLayout.setupWithViewPager( mViewPager );
        }

        Log.d( TAG, "calling tryIntent() from onCreate()" );
        tryIntent( getIntent() );
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        Log.d( TAG, "calling tryIntent() from onNewIntent()" );
        tryIntent( intent );
    }

    @Override
    public void onRequestPermissionsResult( int requestCode,
                                            @NonNull String[] permissions,
                                            @NonNull int[] grantResults )
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if ( requestCode == REQUEST_WRITE_CODE && haveWritePerm( permissions, grantResults ) ) {
            writeFile();
        }
    }

    static boolean haveWritePerm(@NonNull String[] permissions, @NonNull int[] grantResults)
    {
        boolean found = false;
        for ( int ii = 0; ii < permissions.length && !found; ++ii ) {
            found = permissions[ii].equals(MainActivity.PERMS_ARR[0])
                && PackageManager.PERMISSION_GRANTED == grantResults[ii];
        }
        return found;
    }

    static void uninstall(Activity activity )
    {
        Intent intent = new Intent(Intent.ACTION_DELETE)
            .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID ) )
            .putExtra( "android.intent.extra.UNINSTALL_ALL_USERS", true);
        activity.startActivity( intent );
        activity.finish();
    }

    private void tryIntent( Intent intent )
    {
        String name = intent.getStringExtra( GOT_FILE_NAME );
        String sum = intent.getStringExtra( GOT_FILE_SUM );
        if ( null != name && null != sum ) {
            if ( needWritePermission() ) {
                mName = name;
                mSum = sum;
            } else {
                writeFile( name, sum );
            }
        }
    }

    private boolean needWritePermission()
    {
        boolean needIt = ContextCompat.checkSelfPermission( this, PERMS_ARR[0] )
            != PackageManager.PERMISSION_GRANTED;
        if ( needIt ) {
            ActivityCompat.requestPermissions( this, PERMS_ARR, REQUEST_WRITE_CODE );
        }
        return needIt;
    }

    private void writeFile()
    {
        if ( null != mName && null != mSum ) {
            writeFile( mName, mSum );
            mName = mSum = null;
        }
    }

    private void writeFile( String name, String sum )
    {
        File dir = ClipFragment.getFilesDir();
        FileStore.writeFileTo( this, dir, name, sum );
    }

    private void showHideDisabled()
    {
        boolean enabled = NFCUtils.nfcEnabled( this );
        findViewById(R.id.disabled_expl).setVisibility( enabled ? View.GONE : View.VISIBLE );
        findViewById(R.id.send).setVisibility( !enabled ? View.GONE : View.VISIBLE );
    }

    static Intent getSelfIntent( Context context )
    {
        Intent intent = new Intent( context, MainActivity.class )
            .setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP )
            ;
        return intent;
    }

    static Intent getGotFileIntent( Context context, String name, String sum )
    {
        Intent intent = getSelfIntent( context )
            .putExtra( GOT_FILE_NAME, name )
            .putExtra( GOT_FILE_SUM, sum )
            ;
        return intent;
    }
}

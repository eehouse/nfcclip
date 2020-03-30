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

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    private ViewPager mViewPager;

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
                            uninstall();
                        }
                    } );
        } else {
            setContentView( R.layout.main_pager );

            mViewPager = (ViewPager)findViewById( R.id.viewpager );
            PageFragment.addAdapter( getSupportFragmentManager(), mViewPager );
            TabLayout tabLayout = (TabLayout)findViewById( R.id.sliding_tabs );
            tabLayout.setupWithViewPager( mViewPager );
        }
    }

    private void uninstall()
    {
        Intent intent = new Intent(Intent.ACTION_DELETE)
            .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID ) )
            .putExtra( "android.intent.extra.UNINSTALL_ALL_USERS", true);
        startActivity( intent );
        finish();
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
}

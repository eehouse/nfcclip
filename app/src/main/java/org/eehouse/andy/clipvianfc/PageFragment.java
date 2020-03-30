/* -*- compile-command: "find-and-gradle.sh inDeb"; -*- */
/*
 * Copyright 2019 by Eric House (eehouse@eehouse.org).  All rights reserved.
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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

public class PageFragment extends Fragment {
    private static final String ARG_PAGE_TITLE = "ARG_PAGE_TITLE";
    private static final String ARG_PAGE_POS = "ARG_PAGE_POS";

    private int mPageIndex;

    static final int sLayouts[] = { R.layout.fragment_clip,
                                    R.layout.fragment_about,
    };

    public static PageFragment newInstance( int pos )
    {
        PageFragment result = null;
        Bundle args = new Bundle();
        args.putInt( ARG_PAGE_POS, pos );
        switch ( sLayouts[pos] ) {
        case R.layout.fragment_about:
            result = new AboutFragment();
            break;
        case R.layout.fragment_clip:
            result = new ClipFragment();
            break;
        }
        result.setArguments( args );
        return result;
    }

    public static void addAdapter( FragmentManager fm, ViewPager pager )
    {
        Context context = pager.getContext();
        FragmentPagerAdapterImpl adapter = new FragmentPagerAdapterImpl( fm, context );
        pager.setAdapter( adapter );
    }

    @Override
    public void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        mPageIndex = getArguments().getInt( ARG_PAGE_POS );
    }

    @Override
    public View onCreateView( LayoutInflater inflater,
                              ViewGroup container,
                              Bundle savedInstanceState )
    {
        View view = inflater.inflate(sLayouts[mPageIndex], container, false);
        // onViewCreated( view );

        return view;
    }

    private static final int[] sTabTitles = { R.string.tab_clip, R.string.tab_about, };
    private static class FragmentPagerAdapterImpl extends FragmentPagerAdapter {
        private Context mContext;

        FragmentPagerAdapterImpl( FragmentManager fm, Context context )
        {
            super(fm);
            mContext = context;
        }

        @Override
        public int getCount()
        {
            return sTabTitles.length;
        }

        @Override
        public Fragment getItem(int position)
        {
            return PageFragment.newInstance( position );
        }

        @Override
        public CharSequence getPageTitle( int position )
        {
            // Generate title based on item position
            return mContext.getString( sTabTitles[position] );
        }
    }
}
